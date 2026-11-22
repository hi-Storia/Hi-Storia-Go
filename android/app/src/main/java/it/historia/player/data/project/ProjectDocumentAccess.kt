package it.historia.player.data.project

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream

/** Accesso ai documenti scelti dall'utente con lo Storage Access Framework (nessun permesso storage). */
class ProjectDocumentAccess(private val contentResolver: ContentResolver) {
    fun openOutput(uri: Uri): OutputStream {
        val stream = runCatching { contentResolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: contentResolver.openOutputStream(uri, "w")
        return stream ?: throw FileNotFoundException("Destinazione non disponibile.")
    }

    fun openInput(uri: Uri): InputStream {
        return contentResolver.openInputStream(uri) ?: throw FileNotFoundException("File non disponibile.")
    }

    /** Elimina un documento creato da un'esportazione non riuscita, se il provider lo consente. */
    fun deleteQuietly(uri: Uri) {
        runCatching { DocumentsContract.deleteDocument(contentResolver, uri) }
    }
}
