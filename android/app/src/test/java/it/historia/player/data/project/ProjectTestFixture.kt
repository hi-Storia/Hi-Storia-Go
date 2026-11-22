package it.historia.player.data.project

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import it.historia.player.data.audio.UserAudioStore
import it.historia.player.data.preferences.BackgroundMusicSettings
import it.historia.player.data.preferences.BackgroundMusicSettingsStore
import it.historia.player.data.preferences.ExplorationSettings
import it.historia.player.data.preferences.ExplorationSettingsStore
import it.historia.player.data.preferences.ExplorationTrackSettings
import it.historia.player.data.preferences.IdleSettings
import it.historia.player.data.preferences.IdleSettingsStore
import it.historia.player.data.preferences.IdleTrackSettings
import it.historia.player.data.preferences.PresentationSettings
import it.historia.player.data.preferences.PresentationSettingsStore
import it.historia.player.data.preferences.ProjectSettings
import it.historia.player.data.preferences.ProjectSettingsStore
import it.historia.player.data.preferences.SignalMappingStore
import it.historia.player.domain.model.PlaybackContext
import it.historia.player.domain.player.DefaultAudioAssets
import it.historia.player.domain.player.DeviceCatalog
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Ambiente di test con store reali (Robolectric): simula lo smartphone di origine e quello di
 * destinazione nello stesso processo, sostituendo di volta in volta i dati.
 */
class ProjectTestFixture {
    val context: Context = ApplicationProvider.getApplicationContext()
    val device = DeviceCatalog.devices.first()
    val deviceId = device.deviceId

    val userAudioStore = UserAudioStore(context)
    val projectSettingsStore = ProjectSettingsStore(context)
    val signalMappingStore = SignalMappingStore(context)
    val idleSettingsStore = IdleSettingsStore(context)
    val explorationSettingsStore = ExplorationSettingsStore(context)
    val presentationSettingsStore = PresentationSettingsStore(context)
    val backgroundMusicSettingsStore = BackgroundMusicSettingsStore(context)

    /** Asset "presenti nell'APK" per i test (deterministici, indipendenti dalla cartella assets). */
    val apkAssets = mutableSetOf(
        "audio/default/tracks/00.mp3",
        DefaultAudioAssets.IDLE,
        DefaultAudioAssets.BACKGROUND,
        DefaultAudioAssets.EXPLORATION_INTRO,
        DefaultAudioAssets.EXPLORATION_SIGNAL
    )

    val storage = ProjectStorage(
        userAudioStore = userAudioStore,
        projectSettingsStore = projectSettingsStore,
        signalMappingStore = signalMappingStore,
        idleSettingsStore = idleSettingsStore,
        explorationSettingsStore = explorationSettingsStore,
        presentationSettingsStore = presentationSettingsStore,
        backgroundMusicSettingsStore = backgroundMusicSettingsStore,
        assetExists = { it in apkAssets }
    )

    val workDir = File(context.filesDir, "project_transfer")
    var availableSpace = Long.MAX_VALUE

    val manager = ProjectTransferManager(
        storage = storage,
        workDir = workDir,
        assetExists = { it in apkAssets },
        generator = ArchiveGenerator("Hi-Storia Go", "it.historia.go", "0.1.0-test", 1L),
        clock = { 1_763_800_000_000L },
        usableSpace = { availableSpace }
    )

    val projectAudioDir: File get() = userAudioStore.projectAudioDir(deviceId)

    fun ctx(profile: String, language: String) = PlaybackContext(deviceId, profile, language)

    fun writeAudio(relativeToProject: String, content: String): File {
        val file = File(projectAudioDir, relativeToProject)
        file.parentFile!!.mkdirs()
        file.writeBytes(content.toByteArray())
        return file
    }

    /** Azzera completamente il progetto (come uno smartphone appena installato). */
    fun wipeProject() {
        storage.preferenceSections(deviceId).forEach { it.clear() }
        projectAudioDir.deleteRecursively()
        workDir.deleteRecursively()
    }

    /**
     * Progetto di origine: due tipologie (una personalizzata), tre lingue (una personalizzata),
     * una serie disattivata con contenuti propri, audio di ogni tipo, etichette, volumi,
     * tracce predefinite nascoste e un file raggiunto solo tramite percorso di ripiego.
     */
    fun buildRichProject() {
        wipeProject()
        projectSettingsStore.save(
            deviceId,
            ProjectSettings(
                culturalAssetName = "Castello di Santa Severa",
                schoolName = "Liceo Galilei",
                trackCount = 6,
                profileLabels = mapOf("adult" to "Adulti", "custom_profile_1" to "Bambini"),
                languageLabels = mapOf(
                    "it" to "Italiano",
                    "en" to "English",
                    "custom_lang_1" to "Napoletano"
                ),
                customProfiles = setOf("custom_profile_1"),
                customLanguagesByProfile = mapOf(
                    "adult" to setOf("en"),
                    "custom_profile_1" to setOf("it", "custom_lang_1")
                ),
                enabledSeries = setOf("adult:it", "custom_profile_1:it", "custom_profile_1:custom_lang_1")
            )
        )

        // Audio caricati dall'utente, diversi per serie e per tipologia.
        writeAudio("adult/it/tracks/01-ingresso.mp3", "it-ingresso")
        writeAudio("adult/it/tracks/02.mp3", "it-torre")
        writeAudio("adult/it/tracks/non-associato.m4a", "it-libero")
        writeAudio("adult/en/tracks/01-entrance.mp3", "en-entrance")
        writeAudio("adult/en/presentation/welcome.mp3", "en-welcome")
        writeAudio("adult/it/idle/attesa.mp3", "it-attesa")
        writeAudio("custom_profile_1/it/tracks/fiaba.mp3", "kids-fiaba")
        writeAudio("custom_profile_1/it/exploration/intro.wav", "kids-intro")
        writeAudio("custom_profile_1/custom_lang_1/tracks/storia.ogg", "nap-storia")
        writeAudio("background/musica.mp3", "musica")
        // Raggiungibile solo tramite il ripiego di TrackResolver (adult/<lingua>).
        writeAudio("adult/custom_lang_1/tracks/eco.mp3", "eco")
        // Residuo di una lingua rimossa e mai referenziato: non fa parte del progetto.
        writeAudio("adult/fr/tracks/orfano.mp3", "orfano")

        val adultIt = ctx("adult", "it")
        signalMappingStore.save(adultIt, mapOf(1 to "01-ingresso.mp3", 2 to "02.mp3", 3 to "00.mp3"), 6)
        signalMappingStore.saveLabel(adultIt, 1, "Ingresso", 6)
        signalMappingStore.saveLabel(adultIt, 2, "Torre: piano alto", 6)
        signalMappingStore.saveVolume(adultIt, 1, 80, 6)
        signalMappingStore.saveVolume(adultIt, 2, 35, 6)

        val adultEn = ctx("adult", "en")
        signalMappingStore.save(adultEn, mapOf(1 to "01-entrance.mp3"), 6)
        signalMappingStore.saveLabel(adultEn, 1, "Entrance", 6)
        signalMappingStore.saveVolume(adultEn, 1, 50, 6)

        val kidsIt = ctx("custom_profile_1", "it")
        signalMappingStore.save(kidsIt, mapOf(1 to "fiaba.mp3", 4 to "01-ingresso.mp3"), 6)
        signalMappingStore.saveLabel(kidsIt, 4, "Ingresso bimbi", 6)

        val kidsNap = ctx("custom_profile_1", "custom_lang_1")
        signalMappingStore.save(kidsNap, mapOf(1 to "storia.ogg", 2 to "eco.mp3"), 6)
        userAudioStore.hideAssetTrack(kidsNap, "00.mp3")

        idleSettingsStore.save(deviceId, IdleSettings(timeoutMinutes = 12))
        idleSettingsStore.saveTrackSettings(adultIt, IdleTrackSettings(useDefaultTrack = false, customTrackKey = "attesa.mp3"))
        idleSettingsStore.saveTrackSettings(kidsIt, IdleTrackSettings(useDefaultTrack = true))

        explorationSettingsStore.save(deviceId, ExplorationSettings(timeoutMinutes = 7))
        explorationSettingsStore.saveTrackSettings(
            kidsIt,
            ExplorationTrackSettings(useDefaultTrack = false, customTrackKey = "intro.wav")
        )

        presentationSettingsStore.save(adultEn, PresentationSettings(customTrackKey = "welcome.mp3"))

        backgroundMusicSettingsStore.save(
            deviceId,
            BackgroundMusicSettings(
                enabled = true,
                useDefaultTrack = false,
                customTrackKey = "musica.mp3",
                onlyDuringTracks = true,
                fullVolumePercent = 75f,
                duckedVolumePercent = 12.5f
            )
        )
    }

    /** Progetto già presente sullo smartphone di destinazione, da sostituire. */
    fun buildOtherProject() {
        wipeProject()
        projectSettingsStore.save(
            deviceId,
            ProjectSettings(
                culturalAssetName = "Museo civico",
                schoolName = "IC Verdi",
                trackCount = 3,
                profileLabels = mapOf("adult" to "Visitatori", "custom_profile_1" to "Scuole", "custom_profile_2" to "Esperti"),
                languageLabels = mapOf("it" to "Italiano", "es" to "Spagnolo", "custom_lang_1" to "Sardo"),
                customProfiles = setOf("custom_profile_1", "custom_profile_2"),
                customLanguagesByProfile = mapOf(
                    "adult" to setOf("es"),
                    "custom_profile_1" to setOf("custom_lang_1"),
                    "custom_profile_2" to setOf("it")
                ),
                enabledSeries = setOf("adult:it", "adult:es", "custom_profile_2:it")
            )
        )
        writeAudio("adult/it/tracks/vecchio.mp3", "old")
        writeAudio("adult/es/idle/espera.mp3", "old-idle")
        writeAudio("custom_profile_2/it/tracks/esperti.mp3", "old-experts")
        writeAudio("background/vecchia-musica.mp3", "old-music")
        signalMappingStore.save(ctx("adult", "it"), mapOf(1 to "vecchio.mp3"), 3)
        signalMappingStore.saveLabel(ctx("adult", "es"), 2, "Vecchia etichetta", 3)
        signalMappingStore.save(ctx("custom_profile_2", "it"), mapOf(1 to "esperti.mp3"), 3)
        idleSettingsStore.saveTrackSettings(ctx("adult", "es"), IdleTrackSettings(false, "espera.mp3"))
        presentationSettingsStore.save(ctx("custom_profile_2", "it"), PresentationSettings("esperti.mp3"))
        userAudioStore.hideAssetTrack(ctx("adult", "it"), "00.mp3")
        backgroundMusicSettingsStore.save(
            deviceId,
            BackgroundMusicSettings(enabled = true, useDefaultTrack = false, customTrackKey = "vecchia-musica.mp3")
        )
    }

    fun exportBytes(): ByteArray {
        val output = ByteArrayOutputStream()
        manager.export(device, output)
        return output.toByteArray()
    }

    fun prepare(bytes: ByteArray): PreparedProjectImport = manager.prepareImport(ByteArrayInputStream(bytes))

    /** Stato osservabile del progetto: snapshot logico + contenuto di ogni file audio. */
    fun projectState(): ProjectState {
        return ProjectState(
            snapshot = storage.collect(device),
            files = audioFiles(),
            preferences = storage.preferenceSections(deviceId).associate { it.preferencesName to it.entries() }
        )
    }

    fun audioFiles(): Map<String, String> {
        val root = projectAudioDir
        if (!root.exists()) return emptyMap()
        return root.walkTopDown()
            .filter { it.isFile }
            .associate { it.relativeTo(root).invariantSeparatorsPath to it.readText() }
            .toSortedMap()
    }

    fun leftoverTransferFiles(): List<String> {
        if (!workDir.exists()) return emptyList()
        return workDir.walkTopDown().filter { it.isFile }.map { it.relativeTo(workDir).invariantSeparatorsPath }.toList()
    }

    data class ProjectState(
        val snapshot: ProjectSnapshot,
        val files: Map<String, String>,
        val preferences: Map<String, Map<String, Any?>>
    )

    companion object {
        fun readZip(bytes: ByteArray): MutableList<Pair<String, ByteArray>> {
            val entries = mutableListOf<Pair<String, ByteArray>>()
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries += entry.name to zip.readBytes()
                }
            }
            return entries
        }

        fun writeZip(entries: List<Pair<String, ByteArray>>): ByteArray {
            val output = ByteArrayOutputStream()
            ZipOutputStream(output).use { zip ->
                entries.forEach { (name, data) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(data)
                    zip.closeEntry()
                }
            }
            return output.toByteArray()
        }

        fun rewrite(bytes: ByteArray, transform: (MutableList<Pair<String, ByteArray>>) -> Unit): ByteArray {
            val entries = readZip(bytes)
            transform(entries)
            return writeZip(entries)
        }

        fun MutableList<Pair<String, ByteArray>>.replaceText(name: String, transform: (String) -> String) {
            val index = indexOfFirst { it.first == name }
            require(index >= 0) { "voce $name assente" }
            this[index] = name to transform(String(this[index].second)).toByteArray()
        }
    }
}
