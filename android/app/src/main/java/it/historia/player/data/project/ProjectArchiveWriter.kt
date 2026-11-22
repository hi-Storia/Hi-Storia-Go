package it.historia.player.data.project

import it.historia.player.data.project.ProjectArchiveException.Reason
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Scrive un archivio `*.historia.zip`. Il manifest è sempre la prima voce. */
object ProjectArchiveWriter {
    private const val BUFFER_SIZE = 64 * 1024

    /** Calcola le voci del manifest (dimensione e SHA-256) per i file da esportare. */
    fun describeFiles(files: List<ProjectAudioFile>): List<ArchiveFileEntry> {
        return files.map { audio ->
            val (size, sha256) = digest(audio.file)
            if (size > ProjectArchiveFormat.MAX_AUDIO_FILE_BYTES) {
                archiveError(
                    Reason.LIMIT_EXCEEDED,
                    "Il file ${audio.location.fileName} supera la dimensione massima esportabile."
                )
            }
            ArchiveFileEntry(audio.location.archivePath, size, sha256)
        }
    }

    fun write(
        output: OutputStream,
        manifestJson: String,
        projectJson: String,
        files: List<ProjectAudioFile>,
        entries: List<ArchiveFileEntry>
    ) {
        require(files.size == entries.size) { "File e voci del manifest non corrispondono" }
        val zip = ZipOutputStream(output.buffered(BUFFER_SIZE))
        zip.setLevel(Deflater.DEFAULT_COMPRESSION)
        zip.putText(ProjectArchiveFormat.MANIFEST_ENTRY, manifestJson)
        zip.putText(ProjectArchiveFormat.PROJECT_ENTRY, projectJson)

        // Gli audio sono già compressi: compressione rapida per non sprecare CPU.
        zip.setLevel(Deflater.BEST_SPEED)
        files.zip(entries).forEach { (audio, entry) ->
            zip.putNextEntry(ZipEntry(entry.path))
            val messageDigest = MessageDigest.getInstance("SHA-256")
            var written = 0L
            audio.file.inputStream().use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    messageDigest.update(buffer, 0, read)
                    zip.write(buffer, 0, read)
                    written += read
                }
            }
            zip.closeEntry()
            if (written != entry.size || messageDigest.digest().toHex() != entry.sha256) {
                throw IOException("Il file ${audio.location.fileName} è cambiato durante l'esportazione.")
            }
        }
        zip.finish()
        zip.flush()
    }

    private fun ZipOutputStream.putText(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun digest(file: File): Pair<Long, String> {
        val messageDigest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                messageDigest.update(buffer, 0, read)
                size += read
            }
        }
        return size to messageDigest.digest().toHex()
    }
}

internal fun ByteArray.toHex(): String {
    val chars = CharArray(size * 2)
    forEachIndexed { index, byte ->
        val value = byte.toInt() and 0xFF
        chars[index * 2] = HEX_DIGITS[value ushr 4]
        chars[index * 2 + 1] = HEX_DIGITS[value and 0x0F]
    }
    return String(chars)
}

private val HEX_DIGITS = "0123456789abcdef".toCharArray()
