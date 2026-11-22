package it.historia.player.data.project

import java.text.Normalizer
import java.util.Locale

/**
 * Costanti e regole del formato `*.historia.zip` (vedi `docs/project-transfer.md`).
 *
 * Struttura dell'archivio:
 * ```
 * manifest.json                                    (sempre la prima voce)
 * project.json
 * audio/series/<profilo>/<lingua>/<tipo>/<file>    tipo = tracks | idle | exploration | presentation
 * audio/background/<file>
 * ```
 */
object ProjectArchiveFormat {
    const val FORMAT_ID = "it.historia.go.project"
    const val SCHEMA_VERSION = 1

    const val MANIFEST_ENTRY = "manifest.json"
    const val PROJECT_ENTRY = "project.json"
    const val FILE_SUFFIX = ".historia.zip"
    const val MIME_TYPE = "application/zip"
    val OPEN_MIME_TYPES = arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")

    /** Numero massimo di voci nell'archivio (manifest e project compresi). */
    const val MAX_ENTRY_COUNT = 2_000
    /** Dimensione massima, decompressa, di `manifest.json` e `project.json`. */
    const val MAX_JSON_BYTES = 4L * 1024 * 1024
    /** Dimensione massima, decompressa, di un singolo file audio. */
    const val MAX_AUDIO_FILE_BYTES = 300L * 1024 * 1024
    /** Dimensione massima complessiva dei file audio decompressi. */
    const val MAX_TOTAL_AUDIO_BYTES = 4L * 1024 * 1024 * 1024
    /** Spazio libero minimo che deve restare sul dispositivo dopo l'estrazione. */
    const val FREE_SPACE_MARGIN_BYTES = 20L * 1024 * 1024

    const val MAX_ID_LENGTH = 64
    const val MAX_FILE_NAME_LENGTH = 96
    const val MAX_LABEL_LENGTH = 200
    const val MAX_PROJECT_TEXT_LENGTH = 500
    const val MAX_SIGNAL = 99

    val SUPPORTED_AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "wav", "ogg", "opus")

    private const val AUDIO_ROOT = "audio"
    private const val SERIES_ROOT = "series"
    private const val BACKGROUND_DIR = "background"

    private val ID_REGEX = Regex("^[A-Za-z0-9_-]{1,$MAX_ID_LENGTH}$")
    private val FILE_NAME_REGEX = Regex("^[A-Za-z0-9_-][A-Za-z0-9._ -]{0,${MAX_FILE_NAME_LENGTH - 1}}$")

    fun isValidId(value: String): Boolean = ID_REGEX.matches(value)

    /**
     * Nome file audio sicuro: un solo segmento (nessun separatore), primo carattere non `.`,
     * solo caratteri prodotti da `UserAudioStore` e un'estensione audio supportata.
     */
    fun isValidAudioFileName(value: String): Boolean {
        if (!FILE_NAME_REGEX.matches(value)) return false
        val extension = value.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return extension in SUPPORTED_AUDIO_EXTENSIONS
    }

    /** Chiave traccia usabile in un mapping: stessa regola dei nomi file. */
    fun isValidTrackKey(value: String): Boolean = isValidAudioFileName(value)

    fun archivePath(location: ProjectAudioLocation): String {
        return "$AUDIO_ROOT/" + when (location.kind) {
            ProjectAudioKind.BACKGROUND -> "$BACKGROUND_DIR/${location.fileName}"
            else -> "$SERIES_ROOT/${location.profileId}/${location.language}/${location.kind.dirName}/${location.fileName}"
        }
    }

    /** Percorso relativo alla cartella audio del progetto sul dispositivo (`filesDir/audio/<deviceId>`). */
    fun projectRelativePath(location: ProjectAudioLocation): String {
        return when (location.kind) {
            ProjectAudioKind.BACKGROUND -> "$BACKGROUND_DIR/${location.fileName}"
            else -> "${location.profileId}/${location.language}/${location.kind.dirName}/${location.fileName}"
        }
    }

    /**
     * Interpreta il percorso di una voce audio dell'archivio. Restituisce `null` per qualsiasi
     * percorso non conforme (assoluto, con `..`, con backslash, con segmenti non validi...).
     */
    fun parseArchivePath(path: String): ProjectAudioLocation? {
        if (path.isEmpty() || path.length > 512) return null
        if (path.contains('\\') || path.startsWith("/") || path.contains('\u0000')) return null
        val segments = path.split('/')
        if (segments.firstOrNull() != AUDIO_ROOT) return null
        return when {
            segments.size == 3 && segments[1] == BACKGROUND_DIR -> {
                val fileName = segments[2]
                if (!isValidAudioFileName(fileName)) null
                else ProjectAudioLocation(ProjectAudioKind.BACKGROUND, null, null, fileName)
            }
            segments.size == 6 && segments[1] == SERIES_ROOT -> {
                val profileId = segments[2]
                val language = segments[3]
                val kind = ProjectAudioKind.seriesKindForDir(segments[4])
                val fileName = segments[5]
                if (!isValidId(profileId) || !isValidId(language) || kind == null || !isValidAudioFileName(fileName)) {
                    null
                } else {
                    ProjectAudioLocation(kind, profileId, language, fileName)
                }
            }
            else -> null
        }
    }

    /** Interpreta un percorso relativo alla cartella audio del progetto sul dispositivo. */
    fun parseProjectRelativePath(path: String): ProjectAudioLocation? {
        val segments = path.split('/')
        val archivePath = when (segments.size) {
            2 -> "$AUDIO_ROOT/$path"
            4 -> "$AUDIO_ROOT/$SERIES_ROOT/$path"
            else -> return null
        }
        return parseArchivePath(archivePath)
    }

    /**
     * Controlli preliminari sul nome di una voce ZIP prima di qualsiasi interpretazione:
     * blocca ZIP Slip / path traversal anche per le voci di tipo cartella.
     */
    fun isSafeEntryName(name: String): Boolean {
        if (name.isEmpty() || name.length > 512) return false
        if (name.contains('\\') || name.contains('\u0000') || name.contains(':')) return false
        if (name.startsWith("/")) return false
        val segments = name.trimEnd('/').split('/')
        return segments.none { it.isEmpty() || it == "." || it == ".." }
    }

    /** Nome file suggerito per l'esportazione: `nome-progetto.historia.zip`. */
    fun suggestedFileName(projectName: String): String {
        val slug = Normalizer.normalize(projectName.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(60)
            .trim('-')
            .ifBlank { "progetto" }
        return "$slug$FILE_SUFFIX"
    }
}

enum class ProjectAudioKind(val dirName: String) {
    TRACKS("tracks"),
    IDLE("idle"),
    EXPLORATION("exploration"),
    PRESENTATION("presentation"),
    BACKGROUND("background");

    companion object {
        val SERIES_KINDS = listOf(TRACKS, IDLE, EXPLORATION, PRESENTATION)

        fun seriesKindForDir(dirName: String): ProjectAudioKind? {
            return SERIES_KINDS.firstOrNull { it.dirName == dirName }
        }
    }
}

/** Posizione logica di un file audio dell'utente, indipendente dal dispositivo e dall'archivio. */
data class ProjectAudioLocation(
    val kind: ProjectAudioKind,
    val profileId: String?,
    val language: String?,
    val fileName: String
) {
    val archivePath: String get() = ProjectArchiveFormat.archivePath(this)
    val projectRelativePath: String get() = ProjectArchiveFormat.projectRelativePath(this)

    companion object {
        fun series(kind: ProjectAudioKind, profileId: String, language: String, fileName: String) =
            ProjectAudioLocation(kind, profileId, language, fileName)

        fun background(fileName: String) =
            ProjectAudioLocation(ProjectAudioKind.BACKGROUND, null, null, fileName)
    }
}
