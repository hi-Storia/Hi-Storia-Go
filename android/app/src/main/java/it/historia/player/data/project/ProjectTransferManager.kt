package it.historia.player.data.project

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import it.historia.player.data.audio.UserAudioStore
import it.historia.player.data.preferences.BackgroundMusicSettingsStore
import it.historia.player.data.preferences.ExplorationSettingsStore
import it.historia.player.data.preferences.IdleSettingsStore
import it.historia.player.data.preferences.PresentationSettingsStore
import it.historia.player.data.preferences.ProjectSettingsStore
import it.historia.player.data.preferences.SignalMappingStore
import it.historia.player.data.project.ProjectArchiveException.Reason
import it.historia.player.domain.player.DeviceCatalog
import it.historia.player.domain.player.DeviceDefinition
import it.historia.player.domain.player.TrackResolver
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.util.UUID

data class ProjectExportResult(
    val audioFileCount: Int,
    val totalAudioBytes: Long
)

data class LanguageSummary(val label: String, val enabled: Boolean)

data class ProfileSummary(val label: String, val languages: List<LanguageSummary>)

/** Riepilogo mostrato all'utente prima di confermare la sostituzione del progetto. */
data class ProjectImportSummary(
    val projectName: String,
    val schoolName: String,
    val trackCount: Int,
    val profiles: List<ProfileSummary>,
    val seriesCount: Int,
    val enabledSeriesCount: Int,
    val audioFileCount: Int,
    val totalAudioBytes: Long,
    val createdAt: String,
    val sourceAppVersion: String,
    val schemaVersion: Int,
    val warnings: List<String>
)

/** Archivio validato ed estratto nell'area temporanea, in attesa di conferma. */
class PreparedProjectImport internal constructor(
    val id: String,
    internal val stagingDir: File,
    val snapshot: ProjectSnapshot,
    val manifest: ProjectArchiveManifest,
    val summary: ProjectImportSummary
)

/** Punti dell'importazione in cui i test possono simulare un guasto. */
internal enum class ImportStage {
    PREFERENCES_BACKED_UP,
    AUDIO_BACKED_UP,
    AUDIO_INSTALLED,
    PREFERENCES_WRITTEN
}

/**
 * Esportazione e importazione del progetto corrente.
 *
 * L'importazione è in due fasi:
 * 1. [prepareImport] legge, valida ed estrae l'archivio in `project_transfer/staging/<id>`
 *    senza toccare il progetto corrente;
 * 2. [applyImport] (dopo la conferma dell'utente) salva una copia di sicurezza delle preferenze
 *    del progetto, sposta la cartella audio corrente in `project_transfer/backup`, installa i
 *    nuovi file e scrive le preferenze. Un journal su disco registra la fase: in caso di errore
 *    il progetto precedente viene ripristinato subito, in caso di interruzione del processo al
 *    successivo avvio ([recoverInterruptedImport]).
 *
 * Tutti i metodi eseguono I/O bloccante: vanno chiamati fuori dal thread principale.
 */
class ProjectTransferManager(
    private val storage: ProjectStorage,
    private val workDir: File,
    private val assetExists: (String) -> Boolean,
    private val generator: ArchiveGenerator,
    private val deviceLookup: (String) -> DeviceDefinition? = DeviceCatalog::findDevice,
    private val clock: () -> Long = System::currentTimeMillis,
    private val usableSpace: (File) -> Long = { directory -> directory.usableSpace }
) {
    private val lock = Any()
    private val stagingRoot get() = File(workDir, STAGING_DIR)
    private val backupDir get() = File(workDir, BACKUP_DIR)
    private val journalFile get() = File(workDir, JOURNAL_FILE)

    /** Solo per i test: simula un guasto in un punto preciso dell'importazione. */
    @Volatile
    internal var faultInjector: ((ImportStage) -> Unit)? = null

    // ------------------------------------------------------------------ export

    fun export(device: DeviceDefinition, output: OutputStream): ProjectExportResult = synchronized(lock) {
        try {
            val snapshot = storage.collect(device)
            val projectJson = ProjectJsonCodec.encodeProject(snapshot, device)
            // Non produrre mai un archivio che questa stessa versione non saprebbe importare.
            try {
                ProjectJsonCodec.decodeProject(projectJson) { id -> device.takeIf { it.deviceId == id } }
            } catch (error: ProjectArchiveException) {
                archiveError(Reason.INVALID_MANIFEST, "Il progetto contiene dati non esportabili: ${error.message}", error)
            }

            val files = storage.audioFilesFor(snapshot)
            if (files.size > ProjectArchiveFormat.MAX_ENTRY_COUNT - 2) {
                archiveError(Reason.LIMIT_EXCEEDED, "Il progetto contiene troppi file audio da esportare.")
            }
            val entries = ProjectArchiveWriter.describeFiles(files)
            val manifest = ProjectArchiveManifest(
                schemaVersion = ProjectArchiveFormat.SCHEMA_VERSION,
                createdAt = Instant.ofEpochMilli(clock()).toString(),
                generator = generator,
                deviceId = snapshot.deviceId,
                projectName = snapshot.settings.culturalAssetName,
                schoolName = snapshot.settings.schoolName,
                files = entries,
                assetDependencies = storage.assetDependenciesFor(snapshot).toList()
            )
            if (manifest.totalAudioBytes > ProjectArchiveFormat.MAX_TOTAL_AUDIO_BYTES) {
                archiveError(Reason.LIMIT_EXCEEDED, "Il contenuto audio supera la dimensione massima esportabile.")
            }
            ProjectArchiveWriter.write(
                output = output,
                manifestJson = ProjectJsonCodec.encodeManifest(manifest, snapshot),
                projectJson = projectJson,
                files = files,
                entries = entries
            )
            ProjectExportResult(audioFileCount = files.size, totalAudioBytes = manifest.totalAudioBytes)
        } catch (error: ProjectArchiveException) {
            throw error
        } catch (error: IOException) {
            if (error.isNoSpaceError()) {
                archiveError(Reason.INSUFFICIENT_SPACE, "Spazio insufficiente nella destinazione scelta.", error)
            }
            archiveError(Reason.IO_ERROR, "Errore durante la scrittura dell'archivio: ${error.message.orEmpty()}", error)
        }
    }

    // ------------------------------------------------------------------ import

    fun prepareImport(input: InputStream): PreparedProjectImport = synchronized(lock) {
        val id = UUID.randomUUID().toString()
        val stagingDir = File(stagingRoot, id)
        try {
            if (!workDir.isDirectory && !workDir.mkdirs()) throw IOException("Cartella di lavoro non disponibile.")
            val staged = ProjectArchiveReader(deviceLookup).read(
                input = input,
                stagingAudioDir = File(stagingDir, AUDIO_DIR),
                usableSpace = { usableSpace(workDir) }
            )
            PreparedProjectImport(
                id = id,
                stagingDir = stagingDir,
                snapshot = staged.snapshot,
                manifest = staged.manifest,
                summary = summarize(staged)
            )
        } catch (error: Throwable) {
            stagingDir.deleteRecursively()
            when (error) {
                is ProjectArchiveException -> throw error
                is IOException -> if (error.isNoSpaceError()) {
                    archiveError(Reason.INSUFFICIENT_SPACE, "Spazio insufficiente per leggere l'archivio.", error)
                } else {
                    archiveError(Reason.IO_ERROR, "Impossibile leggere l'archivio: ${error.message.orEmpty()}", error)
                }
                is RuntimeException -> archiveError(Reason.CORRUPTED_ARCHIVE, "L'archivio non è leggibile.", error)
                else -> throw error
            }
        }
    }

    fun discardImport(prepared: PreparedProjectImport) {
        prepared.stagingDir.deleteRecursively()
    }

    fun applyImport(prepared: PreparedProjectImport): Unit = synchronized(lock) {
        if (!prepared.stagingDir.isDirectory) {
            archiveError(Reason.IO_ERROR, "L'archivio preparato non è più disponibile: selezionalo di nuovo.")
        }
        recoverInterruptedImportLocked()

        val deviceId = prepared.snapshot.deviceId
        val liveAudio = storage.projectAudioDir(deviceId)
        backupDir.deleteRecursively()
        if (!backupDir.mkdirs()) {
            prepared.stagingDir.deleteRecursively()
            archiveError(Reason.IO_ERROR, "Impossibile preparare la copia di sicurezza del progetto.")
        }

        try {
            writeJournal(PHASE_BACKUP, deviceId)
            writeTextAtomically(
                File(backupDir, PREFERENCES_BACKUP_FILE),
                ProjectPreferencesBackup.encode(storage.preferenceSections(deviceId))
            )
            fault(ImportStage.PREFERENCES_BACKED_UP)
            if (liveAudio.exists()) moveDirectory(liveAudio, File(backupDir, AUDIO_DIR))
            fault(ImportStage.AUDIO_BACKED_UP)

            writeJournal(PHASE_APPLY, deviceId)
            val stagedAudio = File(prepared.stagingDir, AUDIO_DIR)
            if (stagedAudio.isDirectory) moveDirectory(stagedAudio, liveAudio) else liveAudio.mkdirs()
            fault(ImportStage.AUDIO_INSTALLED)
            storage.writeProject(prepared.snapshot)
            fault(ImportStage.PREFERENCES_WRITTEN)
            verifyInstalled(prepared, liveAudio)
            writeJournal(PHASE_COMMITTED, deviceId)
        } catch (error: Throwable) {
            val rollbackError = runCatching { rollbackLocked() }.exceptionOrNull()
            prepared.stagingDir.deleteRecursively()
            if (rollbackError != null) {
                error.addSuppressed(rollbackError)
                archiveError(
                    Reason.IO_ERROR,
                    "Importazione non riuscita. Il ripristino del progetto precedente verrà completato " +
                        "al prossimo avvio dell'app.",
                    error
                )
            }
            val detail = if (error.isNoSpaceError()) " Spazio di archiviazione insufficiente." else ""
            archiveError(
                Reason.IMPORT_ROLLED_BACK,
                "Importazione non riuscita: il progetto precedente è stato ripristinato.$detail",
                error
            )
        }

        backupDir.deleteRecursively()
        journalFile.delete()
        prepared.stagingDir.deleteRecursively()
    }

    /**
     * Da chiamare all'avvio, prima di leggere gli store: completa il rollback di un'importazione
     * interrotta e rimuove aree temporanee rimaste. Restituisce `true` se è stato ripristinato
     * il progetto precedente.
     */
    fun recoverInterruptedImport(): Boolean = synchronized(lock) {
        val recovered = recoverInterruptedImportLocked()
        stagingRoot.deleteRecursively()
        recovered
    }

    private fun recoverInterruptedImportLocked(): Boolean {
        val journal = readJournal()
        if (journal == null) {
            if (!journalFile.exists()) backupDir.deleteRecursively()
            return false
        }
        return when (journal.phase) {
            PHASE_COMMITTED -> {
                backupDir.deleteRecursively()
                journalFile.delete()
                false
            }
            PHASE_BACKUP, PHASE_APPLY -> {
                rollbackLocked()
                true
            }
            else -> false
        }
    }

    /** Ripristina il progetto dalla copia di sicurezza, secondo la fase registrata nel journal. */
    private fun rollbackLocked() {
        val journal = readJournal() ?: return
        val liveAudio = storage.projectAudioDir(journal.deviceId)
        val backupAudio = File(backupDir, AUDIO_DIR)
        if (journal.phase == PHASE_APPLY || backupAudio.exists()) {
            if (!liveAudio.deleteRecursively()) throw IOException("Impossibile rimuovere i file importati.")
            if (backupAudio.exists()) moveDirectory(backupAudio, liveAudio)
        }
        if (journal.phase == PHASE_APPLY) {
            val preferencesBackup = File(backupDir, PREFERENCES_BACKUP_FILE)
            ProjectPreferencesBackup.restore(
                storage.preferenceSections(journal.deviceId),
                preferencesBackup.readText()
            )
        }
        backupDir.deleteRecursively()
        journalFile.delete()
    }

    private fun verifyInstalled(prepared: PreparedProjectImport, liveAudio: File) {
        prepared.manifest.files.forEach { entry ->
            val location = ProjectArchiveFormat.parseArchivePath(entry.path)
                ?: throw IOException("Percorso non valido: ${entry.path}")
            val file = File(liveAudio, location.projectRelativePath)
            if (!file.isFile || file.length() != entry.size) {
                throw IOException("File non installato correttamente: ${location.fileName}")
            }
        }
    }

    private fun fault(stage: ImportStage) {
        faultInjector?.invoke(stage)
    }

    // ------------------------------------------------------------------ summary & checks

    private fun summarize(staged: StagedProjectArchive): ProjectImportSummary {
        val snapshot = staged.snapshot
        val settings = snapshot.settings
        val profiles = snapshot.series
            .groupBy { it.profileId }
            .map { (profileId, series) ->
                ProfileSummary(
                    label = settings.profileLabels[profileId]?.ifBlank { null } ?: profileId,
                    languages = series.map { item ->
                        LanguageSummary(
                            label = settings.languageLabels[item.language]?.ifBlank { null } ?: item.language,
                            enabled = settings.enabledSeries.contains(
                                ProjectSettingsStore.seriesKey(item.profileId, item.language)
                            )
                        )
                    }
                )
            }
        val generatorInfo = staged.manifest.generator
        return ProjectImportSummary(
            projectName = settings.culturalAssetName,
            schoolName = settings.schoolName,
            trackCount = settings.trackCount,
            profiles = profiles,
            seriesCount = snapshot.series.size,
            enabledSeriesCount = settings.enabledSeries.size,
            audioFileCount = staged.files.size,
            totalAudioBytes = staged.manifest.totalAudioBytes,
            createdAt = staged.manifest.createdAt,
            sourceAppVersion = listOf(generatorInfo.appName, generatorInfo.versionName)
                .filter { it.isNotBlank() }
                .joinToString(" "),
            schemaVersion = staged.manifest.schemaVersion,
            warnings = referenceWarnings(staged)
        )
    }

    /**
     * Riferimenti che, dopo l'importazione, non porterebbero a un file riproducibile. Non bloccano
     * l'importazione (lo stesso stato può esistere sullo smartphone di origine) ma vengono mostrati.
     */
    internal fun referenceWarnings(staged: StagedProjectArchive): List<String> {
        val snapshot = staged.snapshot
        val settings = snapshot.settings
        val stagedPaths = staged.files.map { it.projectRelativePath }.toSet()
        val projectPrefix = "audio/${snapshot.deviceId}/"
        val defaults = SignalMappingStore.defaultMapping(settings.trackCount)
        val warnings = mutableListOf<String>()

        fun playable(path: String): Boolean {
            return (path.startsWith(projectPrefix) && path.removePrefix(projectPrefix) in stagedPaths) ||
                assetExists(path)
        }

        fun hasSeriesFile(series: SeriesSnapshot, kind: ProjectAudioKind, fileName: String): Boolean {
            return ProjectAudioLocation.series(kind, series.profileId, series.language, fileName)
                .projectRelativePath in stagedPaths
        }

        snapshot.series.forEach { series ->
            val seriesLabel = listOf(
                settings.profileLabels[series.profileId]?.ifBlank { null } ?: series.profileId,
                settings.languageLabels[series.language]?.ifBlank { null } ?: series.language
            ).joinToString(" / ")
            val context = series.context(snapshot.deviceId)
            val missingSignals = (1..settings.trackCount).filter { signal ->
                val trackKey = series.trackMappings[signal] ?: defaults[signal] ?: return@filter false
                TrackResolver.candidates(trackKey, context).none(::playable)
            }
            if (missingSignals.isNotEmpty()) {
                warnings += "$seriesLabel: audio non trovato per " +
                    missingSignals.take(8).joinToString(", ") { "T$it" } +
                    if (missingSignals.size > 8) "…" else ""
            }
            series.idleTrack.customTrackKey?.let { key ->
                if (!series.idleTrack.useDefaultTrack && !hasSeriesFile(series, ProjectAudioKind.IDLE, key)) {
                    warnings += "$seriesLabel: traccia idle \"$key\" non inclusa."
                }
            }
            series.explorationTrack.customTrackKey?.let { key ->
                if (!series.explorationTrack.useDefaultTrack &&
                    !hasSeriesFile(series, ProjectAudioKind.EXPLORATION, key)
                ) {
                    warnings += "$seriesLabel: traccia esplorazione \"$key\" non inclusa."
                }
            }
            series.presentation.customTrackKey?.let { key ->
                if (!hasSeriesFile(series, ProjectAudioKind.PRESENTATION, key)) {
                    warnings += "$seriesLabel: traccia presentazione \"$key\" non inclusa."
                }
            }
        }
        val background = snapshot.backgroundMusic
        background.customTrackKey?.let { key ->
            if (!background.useDefaultTrack && ProjectAudioLocation.background(key).projectRelativePath !in stagedPaths) {
                warnings += "Musica di sottofondo \"$key\" non inclusa."
            }
        }
        staged.manifest.assetDependencies.filterNot(assetExists).forEach { asset ->
            warnings += "Audio standard non presente in questa versione dell'app: ${asset.substringAfterLast('/')}."
        }
        return warnings.take(MAX_WARNINGS)
    }

    // ------------------------------------------------------------------ files & journal

    private data class Journal(val phase: String, val deviceId: String)

    private fun writeJournal(phase: String, deviceId: String) {
        writeTextAtomically(
            journalFile,
            JSONObject().put("version", 1).put("phase", phase).put("deviceId", deviceId).toString()
        )
    }

    private fun readJournal(): Journal? {
        if (!journalFile.isFile) return null
        return runCatching {
            val json = JSONObject(journalFile.readText())
            Journal(phase = json.getString("phase"), deviceId = json.getString("deviceId"))
        }.getOrNull()?.takeIf { ProjectArchiveFormat.isValidId(it.deviceId) }
    }

    private fun writeTextAtomically(target: File, text: String) {
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, "${target.name}.tmp")
        temporary.writeText(text)
        if (!temporary.renameTo(target)) {
            target.delete()
            if (!temporary.renameTo(target)) throw IOException("Impossibile scrivere ${target.name}.")
        }
    }

    /**
     * Sposta una cartella. Usa `rename` (atomico sullo stesso filesystem); in alternativa copia in
     * una cartella temporanea, la rinomina e solo allora elimina l'origine, così un'interruzione
     * non lascia mai una destinazione parziale.
     */
    private fun moveDirectory(source: File, target: File) {
        target.parentFile?.mkdirs()
        if (target.exists() && !target.deleteRecursively()) throw IOException("Impossibile sostituire ${target.name}.")
        if (source.renameTo(target)) return

        val partial = File(target.parentFile, "${target.name}.partial")
        partial.deleteRecursively()
        if (!source.copyRecursively(partial, overwrite = true)) throw IOException("Copia di ${source.name} non riuscita.")
        if (!partial.renameTo(target)) throw IOException("Impossibile completare lo spostamento di ${source.name}.")
        source.deleteRecursively()
    }

    companion object {
        private const val STAGING_DIR = "staging"
        private const val BACKUP_DIR = "backup"
        private const val AUDIO_DIR = "audio"
        private const val JOURNAL_FILE = "import-journal.json"
        private const val PREFERENCES_BACKUP_FILE = "preferences.json"
        private const val PHASE_BACKUP = "backup"
        private const val PHASE_APPLY = "apply"
        private const val PHASE_COMMITTED = "committed"
        private const val MAX_WARNINGS = 20

        fun create(context: Context): ProjectTransferManager {
            val app = context.applicationContext
            val assetExists: (String) -> Boolean = { path ->
                runCatching { app.assets.open(path).use { }; true }.getOrDefault(false)
            }
            val storage = ProjectStorage(
                userAudioStore = UserAudioStore(app),
                projectSettingsStore = ProjectSettingsStore(app),
                signalMappingStore = SignalMappingStore(app),
                idleSettingsStore = IdleSettingsStore(app),
                explorationSettingsStore = ExplorationSettingsStore(app),
                presentationSettingsStore = PresentationSettingsStore(app),
                backgroundMusicSettingsStore = BackgroundMusicSettingsStore(app),
                assetExists = assetExists
            )
            val generator = runCatching {
                @Suppress("DEPRECATION")
                val packageInfo = app.packageManager.getPackageInfo(app.packageName, 0)
                ArchiveGenerator(
                    appName = app.applicationInfo.loadLabel(app.packageManager).toString(),
                    applicationId = app.packageName,
                    versionName = packageInfo.versionName.orEmpty(),
                    versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
                )
            }.getOrElse { ArchiveGenerator("Hi-Storia Go", app.packageName, "", 0L) }
            return ProjectTransferManager(
                storage = storage,
                workDir = File(app.filesDir, "project_transfer"),
                assetExists = assetExists,
                generator = generator
            )
        }
    }
}
