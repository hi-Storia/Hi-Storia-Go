package it.historia.player.data.project

import it.historia.player.data.audio.UserAudioStore
import it.historia.player.data.preferences.BackgroundMusicSettingsStore
import it.historia.player.data.preferences.ExplorationSettingsStore
import it.historia.player.data.preferences.IdleSettingsStore
import it.historia.player.data.preferences.PresentationSettingsStore
import it.historia.player.data.preferences.ProjectPreferenceSection
import it.historia.player.data.preferences.ProjectSettingsStore
import it.historia.player.data.preferences.SignalMappingStore
import it.historia.player.data.preferences.SignalSeriesData
import it.historia.player.domain.model.PlaybackContext
import it.historia.player.domain.player.DefaultAudioAssets
import it.historia.player.domain.player.DeviceDefinition
import it.historia.player.domain.player.TrackResolver
import java.io.File
import java.io.IOException

/** File audio dell'utente da includere nell'archivio. */
data class ProjectAudioFile(
    val location: ProjectAudioLocation,
    val file: File
)

/**
 * Ponte tra gli store esistenti e il modello [ProjectSnapshot]: raccoglie i dati persistenti
 * del progetto (di tutte le serie, non solo di quella selezionata) e li riscrive in blocco.
 */
class ProjectStorage(
    private val userAudioStore: UserAudioStore,
    private val projectSettingsStore: ProjectSettingsStore,
    private val signalMappingStore: SignalMappingStore,
    private val idleSettingsStore: IdleSettingsStore,
    private val explorationSettingsStore: ExplorationSettingsStore,
    private val presentationSettingsStore: PresentationSettingsStore,
    private val backgroundMusicSettingsStore: BackgroundMusicSettingsStore,
    private val assetExists: (String) -> Boolean
) {
    fun projectAudioDir(deviceId: String): File = userAudioStore.projectAudioDir(deviceId)

    fun collect(device: DeviceDefinition): ProjectSnapshot {
        val deviceId = device.deviceId
        val settings = projectSettingsStore.load(device)
        val series = ProjectSettingsStore.projectSeries(device, settings).map { (profileId, language) ->
            val context = PlaybackContext(deviceId = deviceId, profile = profileId, language = language)
            SeriesSnapshot(
                profileId = profileId,
                language = language,
                trackMappings = signalMappingStore.loadStoredMappings(context),
                labels = signalMappingStore.loadLabels(context, SignalMappingStore.MAX_SIGNAL),
                volumes = signalMappingStore.loadVolumes(context, SignalMappingStore.MAX_SIGNAL),
                hiddenDefaultTracks = userAudioStore.loadHiddenAssetTracks(context),
                idleTrack = idleSettingsStore.loadTrackSettings(context),
                explorationTrack = explorationSettingsStore.loadTrackSettings(context),
                presentation = presentationSettingsStore.load(context)
            )
        }
        return ProjectSnapshot(
            deviceId = deviceId,
            settings = settings,
            idle = idleSettingsStore.load(deviceId),
            exploration = explorationSettingsStore.load(deviceId),
            backgroundMusic = backgroundMusicSettingsStore.load(deviceId),
            series = series
        )
    }

    /**
     * Tutti i file audio dell'utente che appartengono al progetto: le cartelle di ogni serie
     * (anche disattivata), la musica di sottofondo e gli eventuali file di altre cartelle del
     * progetto raggiunti dalle associazioni tramite i percorsi di ripiego di [TrackResolver].
     */
    fun audioFilesFor(snapshot: ProjectSnapshot): List<ProjectAudioFile> {
        val root = projectAudioDir(snapshot.deviceId)
        val files = linkedMapOf<String, ProjectAudioFile>()

        fun addDirectory(directory: File, locationFor: (String) -> ProjectAudioLocation) {
            directory.listFiles()
                .orEmpty()
                .filter { it.isFile && ProjectArchiveFormat.isValidAudioFileName(it.name) }
                .sortedBy { it.name }
                .forEach { file ->
                    val location = locationFor(file.name)
                    files.putIfAbsent(location.archivePath, ProjectAudioFile(location, file))
                }
        }

        snapshot.series.forEach { series ->
            ProjectAudioKind.SERIES_KINDS.forEach { kind ->
                addDirectory(File(root, "${series.profileId}/${series.language}/${kind.dirName}")) { name ->
                    ProjectAudioLocation.series(kind, series.profileId, series.language, name)
                }
            }
        }
        addDirectory(File(root, ProjectAudioKind.BACKGROUND.dirName)) { name ->
            ProjectAudioLocation.background(name)
        }

        val projectPrefix = "audio/${snapshot.deviceId}/"
        snapshot.series.forEach { series ->
            val context = series.context(snapshot.deviceId)
            effectiveTrackKeys(snapshot, series).forEach { trackKey ->
                val resolved = resolveTrack(trackKey, context) ?: return@forEach
                if (!userAudioStore.exists(resolved) || !resolved.startsWith(projectPrefix)) return@forEach
                val location = ProjectArchiveFormat.parseProjectRelativePath(resolved.removePrefix(projectPrefix))
                    ?: return@forEach
                files.putIfAbsent(location.archivePath, ProjectAudioFile(location, userAudioStore.fileFor(resolved)))
            }
        }
        return files.values.toList()
    }

    /** Audio standard dell'APK di cui il progetto ha bisogno (non vengono esportati). */
    fun assetDependenciesFor(snapshot: ProjectSnapshot): Set<String> {
        val dependencies = sortedSetOf<String>()
        snapshot.series.forEach { series ->
            val context = series.context(snapshot.deviceId)
            effectiveTrackKeys(snapshot, series).forEach { trackKey ->
                val resolved = resolveTrack(trackKey, context) ?: return@forEach
                if (!userAudioStore.exists(resolved)) dependencies += resolved
            }
            if (series.idleTrack.useDefaultTrack) dependencies += DefaultAudioAssets.IDLE
            if (series.explorationTrack.useDefaultTrack) dependencies += DefaultAudioAssets.EXPLORATION_INTRO
        }
        dependencies += DefaultAudioAssets.EXPLORATION_SIGNAL
        if (snapshot.backgroundMusic.useDefaultTrack) dependencies += DefaultAudioAssets.BACKGROUND
        return dependencies
    }

    /** Riscrive tutte le preferenze del progetto, eliminando i residui del progetto precedente. */
    fun writeProject(snapshot: ProjectSnapshot) {
        val deviceId = snapshot.deviceId
        val byContext = snapshot.series.associateBy { it.context(deviceId) }
        val results = listOf(
            projectSettingsStore.replaceProject(deviceId, snapshot.settings),
            signalMappingStore.replaceProject(
                deviceId,
                byContext.mapValues { (_, series) ->
                    SignalSeriesData(series.trackMappings, series.labels, series.volumes)
                }
            ),
            userAudioStore.replaceHiddenAssetTracks(deviceId, byContext.mapValues { it.value.hiddenDefaultTracks }),
            idleSettingsStore.replaceProject(deviceId, snapshot.idle, byContext.mapValues { it.value.idleTrack }),
            explorationSettingsStore.replaceProject(
                deviceId,
                snapshot.exploration,
                byContext.mapValues { it.value.explorationTrack }
            ),
            presentationSettingsStore.replaceProject(deviceId, byContext.mapValues { it.value.presentation }),
            backgroundMusicSettingsStore.replaceProject(deviceId, snapshot.backgroundMusic)
        )
        if (results.any { !it }) throw IOException("Scrittura delle impostazioni del progetto non riuscita.")
    }

    /** Sezioni di preferenze del progetto, usate per backup e rollback dell'importazione. */
    fun preferenceSections(deviceId: String): List<ProjectPreferenceSection> = listOf(
        projectSettingsStore.projectSection(deviceId),
        signalMappingStore.projectSection(deviceId),
        userAudioStore.projectSection(deviceId),
        idleSettingsStore.projectSection(deviceId),
        explorationSettingsStore.projectSection(deviceId),
        presentationSettingsStore.projectSection(deviceId),
        backgroundMusicSettingsStore.projectSection(deviceId)
    )

    private fun effectiveTrackKeys(snapshot: ProjectSnapshot, series: SeriesSnapshot): Set<String> {
        val defaults = SignalMappingStore.defaultMapping(SignalMappingStore.MAX_SIGNAL)
        return (1..snapshot.settings.trackCount)
            .mapNotNull { signal -> series.trackMappings[signal] ?: defaults[signal] }
            .plus(series.trackMappings.values)
            .toSet()
    }

    private fun resolveTrack(trackKey: String, context: PlaybackContext): String? {
        return TrackResolver.candidates(trackKey, context).firstOrNull { path ->
            userAudioStore.exists(path) || assetExists(path)
        }
    }
}
