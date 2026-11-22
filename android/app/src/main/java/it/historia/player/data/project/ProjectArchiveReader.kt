package it.historia.player.data.project

import it.historia.player.data.project.ProjectArchiveException.Reason
import it.historia.player.domain.player.DeviceDefinition
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/** Contenuto di un archivio letto, validato ed estratto in un'area temporanea. */
data class StagedProjectArchive(
    val manifest: ProjectArchiveManifest,
    val snapshot: ProjectSnapshot,
    /** File estratti, con percorso relativo alla cartella `audio` dell'area temporanea. */
    val files: List<ProjectAudioLocation>
)

/**
 * Legge un archivio in streaming ed estrae gli audio nella cartella temporanea indicata.
 *
 * Difese applicate:
 * - `manifest.json` deve essere la prima voce: ogni file successivo deve esservi dichiarato,
 *   con dimensione e SHA-256 verificati durante l'estrazione (niente decompression bomb: si
 *   interrompe appena si supera la dimensione dichiarata);
 * - nomi di voce con `..`, percorsi assoluti, backslash, `:` o struttura non prevista vengono
 *   rifiutati e la destinazione canonica deve restare dentro l'area temporanea (ZIP Slip);
 * - limiti a numero di voci, dimensione dei JSON, dei singoli file e del totale.
 */
class ProjectArchiveReader(
    private val deviceLookup: (String) -> DeviceDefinition?
) {
    fun read(input: InputStream, stagingAudioDir: File, usableSpace: () -> Long): StagedProjectArchive {
        if (!stagingAudioDir.isDirectory && !stagingAudioDir.mkdirs()) {
            throw IOException("Impossibile creare l'area temporanea di importazione.")
        }
        val root = stagingAudioDir.canonicalFile
        val zip = ZipInputStream(input.buffered(BUFFER_SIZE))

        val first = zip.nextEntryOrCorrupted()
            ?: archiveError(Reason.CORRUPTED_ARCHIVE, "Il file selezionato è vuoto o non è un archivio ZIP.")
        if (!ProjectArchiveFormat.isSafeEntryName(first.name)) unsafe(first.name)
        if (first.isDirectory || first.name != ProjectArchiveFormat.MANIFEST_ENTRY) {
            archiveError(
                Reason.INVALID_MANIFEST,
                "Manifest mancante: l'archivio non sembra un progetto Hi-Storia Go."
            )
        }
        val manifest = ProjectJsonCodec.decodeManifest(zip.readText())
        val required = manifest.totalAudioBytes + ProjectArchiveFormat.FREE_SPACE_MARGIN_BYTES
        if (usableSpace() < required) {
            archiveError(
                Reason.INSUFFICIENT_SPACE,
                "Spazio insufficiente: servono circa ${formatMegabytes(required)} liberi per importare il progetto."
            )
        }

        val declared = manifest.files.associateBy { it.path }
        val seen = mutableSetOf(ProjectArchiveFormat.MANIFEST_ENTRY)
        val extracted = mutableListOf<ProjectAudioLocation>()
        var projectText: String? = null
        var entryCount = 1

        while (true) {
            val entry = zip.nextEntryOrCorrupted() ?: break
            entryCount++
            if (entryCount > ProjectArchiveFormat.MAX_ENTRY_COUNT) {
                archiveError(Reason.LIMIT_EXCEEDED, "L'archivio contiene troppe voci.")
            }
            val name = entry.name
            if (!ProjectArchiveFormat.isSafeEntryName(name)) unsafe(name)
            if (entry.isDirectory) {
                if (!name.startsWith("audio/")) unexpected(name)
                continue
            }
            if (!seen.add(name)) {
                archiveError(Reason.INVALID_MANIFEST, "Voce duplicata nell'archivio: ${name.take(80)}.")
            }
            if (name == ProjectArchiveFormat.PROJECT_ENTRY) {
                projectText = zip.readText()
                continue
            }
            val location = ProjectArchiveFormat.parseArchivePath(name) ?: unexpected(name)
            val expected = declared[name] ?: archiveError(
                Reason.INVALID_MANIFEST,
                "Il file ${name.take(80)} non è dichiarato nel manifest."
            )
            val target = File(root, location.projectRelativePath).canonicalFile
            if (!target.path.startsWith(root.path + File.separator)) unsafe(name)
            zip.extractTo(target, expected)
            extracted += location
        }

        val text = projectText ?: archiveError(Reason.INVALID_MANIFEST, "Il file project.json è mancante.")
        val missing = declared.keys - seen
        if (missing.isNotEmpty()) {
            archiveError(
                Reason.MISSING_FILE,
                "Archivio incompleto: mancano ${missing.size} file audio (es. ${missing.first().substringAfterLast('/')})."
            )
        }
        val snapshot = ProjectJsonCodec.decodeProject(text, deviceLookup)
        if (snapshot.deviceId != manifest.deviceId) {
            archiveError(Reason.INVALID_MANIFEST, "Manifest e project.json descrivono progetti diversi.")
        }
        return StagedProjectArchive(manifest, snapshot, extracted)
    }

    private fun ZipInputStream.nextEntryOrCorrupted(): ZipEntry? {
        return try {
            nextEntry
        } catch (error: ZipException) {
            // Android 14+ rifiuta già qui i nomi con ".." o "/" iniziale (ZipPathValidator).
            if (error.message.orEmpty().contains("path", ignoreCase = true)) {
                archiveError(Reason.UNSAFE_PATH, "L'archivio contiene percorsi non sicuri.", error)
            }
            archiveError(Reason.CORRUPTED_ARCHIVE, "L'archivio è danneggiato o non è un file ZIP.", error)
        } catch (error: IllegalArgumentException) {
            archiveError(Reason.CORRUPTED_ARCHIVE, "L'archivio contiene nomi di file non leggibili.", error)
        } catch (error: EOFException) {
            archiveError(Reason.CORRUPTED_ARCHIVE, "L'archivio è incompleto o troncato.", error)
        }
    }

    private fun ZipInputStream.readText(): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0L
        readingEntry {
            while (true) {
                val read = read(buffer)
                if (read < 0) break
                total += read
                if (total > ProjectArchiveFormat.MAX_JSON_BYTES) {
                    archiveError(Reason.LIMIT_EXCEEDED, "Il manifest del progetto è troppo grande.")
                }
                output.write(buffer, 0, read)
            }
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private fun ZipInputStream.extractTo(target: File, expected: ArchiveFileEntry) {
        val parent = target.parentFile
        if (parent != null && !parent.isDirectory && !parent.mkdirs()) {
            throw IOException("Impossibile creare la cartella ${parent.name}.")
        }
        val digest = MessageDigest.getInstance("SHA-256")
        var written = 0L
        val fileName = expected.path.substringAfterLast('/')
        try {
            target.outputStream().use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                readingEntry {
                    while (true) {
                        val read = read(buffer)
                        if (read < 0) break
                        written += read
                        if (written > expected.size) {
                            archiveError(
                                Reason.MISSING_FILE,
                                "Il file $fileName è più grande di quanto dichiarato nel manifest."
                            )
                        }
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                    }
                }
            }
        } catch (error: IOException) {
            if (error.isNoSpaceError()) {
                archiveError(Reason.INSUFFICIENT_SPACE, "Spazio insufficiente per estrarre i file audio.", error)
            }
            throw error
        }
        if (written != expected.size) {
            archiveError(Reason.MISSING_FILE, "Il file $fileName è incompleto.")
        }
        if (digest.digest().toHex() != expected.sha256) {
            archiveError(Reason.MISSING_FILE, "Il file $fileName è danneggiato (impronta non corrispondente).")
        }
    }

    /** Traduce gli errori di decompressione dei dati in errori di archivio danneggiato. */
    private inline fun readingEntry(block: () -> Unit) {
        try {
            block()
        } catch (error: ZipException) {
            archiveError(Reason.CORRUPTED_ARCHIVE, "L'archivio è danneggiato.", error)
        } catch (error: EOFException) {
            archiveError(Reason.CORRUPTED_ARCHIVE, "L'archivio è incompleto o troncato.", error)
        }
    }

    private fun unsafe(name: String): Nothing = archiveError(
        Reason.UNSAFE_PATH,
        "L'archivio contiene un percorso non sicuro: \"${name.take(80)}\"."
    )

    private fun unexpected(name: String): Nothing = archiveError(
        Reason.UNSAFE_PATH,
        "L'archivio contiene un file non previsto: \"${name.take(80)}\"."
    )

    companion object {
        private const val BUFFER_SIZE = 64 * 1024

        fun formatMegabytes(bytes: Long): String {
            val megabytes = bytes / (1024.0 * 1024.0)
            return if (megabytes < 10) "%.1f MB".format(java.util.Locale.ITALY, megabytes)
            else "%.0f MB".format(java.util.Locale.ITALY, megabytes)
        }
    }
}
