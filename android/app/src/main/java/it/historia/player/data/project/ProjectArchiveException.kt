package it.historia.player.data.project

/**
 * Errore di esportazione/importazione. [message] è già pensato per essere mostrato all'utente.
 */
class ProjectArchiveException(
    val reason: Reason,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    enum class Reason {
        /** Il file non è uno ZIP leggibile o è troncato. */
        CORRUPTED_ARCHIVE,
        /** Manifest o `project.json` mancanti, malformati o incoerenti. */
        INVALID_MANIFEST,
        /** Versione dello schema più recente di quella supportata da questa app. */
        UNSUPPORTED_VERSION,
        /** Il progetto non è compatibile con la configurazione di questa app. */
        INCOMPATIBLE_PROJECT,
        /** File dichiarati nel manifest ma assenti o diversi da quanto dichiarato. */
        MISSING_FILE,
        /** Voce con percorso pericoloso o non previsto (ZIP Slip, path traversal...). */
        UNSAFE_PATH,
        /** Superati i limiti di dimensione o numero di voci. */
        LIMIT_EXCEEDED,
        /** Spazio di archiviazione insufficiente. */
        INSUFFICIENT_SPACE,
        /** Errore di lettura/scrittura generico. */
        IO_ERROR,
        /** Importazione non riuscita: il progetto precedente è stato ripristinato. */
        IMPORT_ROLLED_BACK
    }
}

internal fun archiveError(
    reason: ProjectArchiveException.Reason,
    message: String,
    cause: Throwable? = null
): Nothing = throw ProjectArchiveException(reason, message, cause)

internal fun Throwable.isNoSpaceError(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        val text = current.message.orEmpty()
        if (text.contains("ENOSPC") || text.contains("No space left", ignoreCase = true)) return true
        current = current.cause
    }
    return false
}
