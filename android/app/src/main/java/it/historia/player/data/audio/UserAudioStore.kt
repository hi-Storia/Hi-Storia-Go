package it.historia.player.data.audio

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import it.historia.player.domain.model.PlaybackContext
import java.io.File
import java.util.Locale

data class UserAudioTrack(
    val trackKey: String,
    val trackId: Int?,
    val fileName: String,
    val relativePath: String,
    val sourceLabel: String
)

class UserAudioStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun importTrack(context: PlaybackContext, sourceUri: Uri): UserAudioTrack? {
        val fileName = uniqueFileNameFor(context, sanitizeFileName(displayNameFor(sourceUri)))
        val destination = fileFor(context, fileName)
        destination.parentFile?.mkdirs()

        return runCatching {
            appContext.contentResolver.openInputStream(sourceUri)?.use { input ->
                destination.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            UserAudioTrack(
                trackKey = fileName,
                trackId = fileName.trackIdOrNull(),
                fileName = destination.name,
                relativePath = relativePathFor(context, fileName),
                sourceLabel = SOURCE_USER
            )
        }.getOrNull()
    }

    fun importIdleTrack(context: PlaybackContext, sourceUri: Uri): UserAudioTrack? {
        val fileName = uniqueIdleFileNameFor(context, sanitizeFileName(displayNameFor(sourceUri)))
        val destination = fileFor(idleRelativePathFor(context, fileName))
        destination.parentFile?.mkdirs()

        return runCatching {
            appContext.contentResolver.openInputStream(sourceUri)?.use { input ->
                destination.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            UserAudioTrack(
                trackKey = fileName,
                trackId = fileName.trackIdOrNull(),
                fileName = destination.name,
                relativePath = idleRelativePathFor(context, fileName),
                sourceLabel = SOURCE_USER
            )
        }.getOrNull()
    }

    fun importExplorationTrack(context: PlaybackContext, sourceUri: Uri): UserAudioTrack? {
        val fileName = uniqueExplorationFileNameFor(context, sanitizeFileName(displayNameFor(sourceUri)))
        val destination = fileFor(explorationRelativePathFor(context, fileName))
        destination.parentFile?.mkdirs()

        return runCatching {
            appContext.contentResolver.openInputStream(sourceUri)?.use { input ->
                destination.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            UserAudioTrack(
                trackKey = fileName,
                trackId = fileName.trackIdOrNull(),
                fileName = destination.name,
                relativePath = explorationRelativePathFor(context, fileName),
                sourceLabel = SOURCE_USER
            )
        }.getOrNull()
    }

    fun importBackgroundTrack(deviceId: String, sourceUri: Uri): UserAudioTrack? {
        val fileName = uniqueBackgroundFileNameFor(deviceId, sanitizeFileName(displayNameFor(sourceUri)))
        val destination = fileFor(backgroundRelativePathFor(deviceId, fileName))
        destination.parentFile?.mkdirs()

        return runCatching {
            appContext.contentResolver.openInputStream(sourceUri)?.use { input ->
                destination.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            UserAudioTrack(
                trackKey = fileName,
                trackId = fileName.trackIdOrNull(),
                fileName = destination.name,
                relativePath = backgroundRelativePathFor(deviceId, fileName),
                sourceLabel = SOURCE_USER
            )
        }.getOrNull()
    }

    fun idleRelativePathFor(context: PlaybackContext, fileName: String): String {
        return "audio/${context.deviceId}/${context.profile}/${context.language}/idle/$fileName"
    }

    fun explorationRelativePathFor(context: PlaybackContext, fileName: String): String {
        return "audio/${context.deviceId}/${context.profile}/${context.language}/exploration/$fileName"
    }

    fun backgroundRelativePathFor(deviceId: String, fileName: String): String {
        return "audio/$deviceId/background/$fileName"
    }

    fun listTracks(context: PlaybackContext): List<UserAudioTrack> {
        return (listUserTracks(context) + listAssetTracks(context))
            .distinctBy { "${it.sourceLabel}:${it.trackKey}" }
            .sortedWith(
                compareBy<UserAudioTrack> { it.trackId ?: Int.MAX_VALUE }
                    .thenBy { it.fileName.lowercase(Locale.ROOT) }
                    .thenBy { it.sourceLabel }
            )
    }

    fun deleteTrack(context: PlaybackContext, trackKey: String): Boolean {
        val file = fileFor(context, trackKey)
        return runCatching {
            if (file.parentFile?.canonicalFile != tracksDirFor(context).canonicalFile) {
                return false
            }
            file.isFile && file.delete()
        }.getOrDefault(false)
    }

    fun deleteUnusedTracks(context: PlaybackContext, usedTrackKeys: Set<String>): Int {
        return listUserTracks(context)
            .filterNot { track -> usedTrackKeys.contains(track.trackKey) }
            .count { track -> deleteTrack(context, track.trackKey) }
    }

    fun hideAssetTrack(context: PlaybackContext, trackKey: String) {
        val key = hiddenAssetsKeyFor(context)
        val updated = prefs.getStringSet(key, null).orEmpty() + trackKey
        prefs.edit()
            .putStringSet(key, updated)
            .apply()
    }

    private fun listUserTracks(context: PlaybackContext): List<UserAudioTrack> {
        val tracksDir = tracksDirFor(context)
        return tracksDir
            .listFiles { file ->
                file.isFile && file.extension.lowercase(Locale.ROOT) in SUPPORTED_AUDIO_EXTENSIONS
            }
            .orEmpty()
            .mapNotNull { file ->
                UserAudioTrack(
                    trackKey = file.name,
                    trackId = file.name.trackIdOrNull(),
                    fileName = file.name,
                    relativePath = relativePathFor(context, file.name),
                    sourceLabel = SOURCE_USER
                )
            }
    }

    private fun listAssetTracks(context: PlaybackContext): List<UserAudioTrack> {
        val assetDirs = listOf("audio/default/tracks", tracksRelativeDirFor(context)).distinct()
        val hiddenAssets = prefs.getStringSet(hiddenAssetsKeyFor(context), null).orEmpty()
        return assetDirs.flatMap { assetDir ->
            appContext.assets.list(assetDir)
                .orEmpty()
                .filter { fileName ->
                    fileName.substringAfterLast(".", "").lowercase(Locale.ROOT) in SUPPORTED_AUDIO_EXTENSIONS
                }
                .filterNot { fileName -> hiddenAssets.contains(fileName) }
                .mapNotNull { fileName ->
                    val trackId = fileName.substringBeforeLast(".").toIntOrNull()
                    UserAudioTrack(
                        trackKey = fileName,
                        trackId = trackId,
                        fileName = fileName,
                        relativePath = "$assetDir/$fileName",
                        sourceLabel = SOURCE_ASSET
                    )
                }
        }.distinctBy { it.trackKey }
    }

    fun fileFor(relativePath: String): File {
        return File(appContext.filesDir, relativePath)
    }

    fun exists(relativePath: String): Boolean {
        return fileFor(relativePath).isFile
    }

    private fun displayNameFor(uri: Uri): String {
        val fromCursor = runCatching {
            appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
            }
        }.getOrNull()
        return fromCursor?.takeIf { it.isNotBlank() } ?: "audio.mp3"
    }

    private fun sanitizeFileName(fileName: String): String {
        val base = fileName.substringBeforeLast(".").ifBlank { "audio" }
        val extension = fileName.substringAfterLast(".", "mp3")
            .lowercase(Locale.ROOT)
            .let { if (it in SUPPORTED_AUDIO_EXTENSIONS) it else "mp3" }
        val sanitizedBase = base
            .replace(Regex("[^A-Za-z0-9._ -]+"), "_")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '.', '_')
            .ifBlank { "audio" }
            .take(48)
            .trim(' ', '.', '_')
            .ifBlank { "audio" }
        return "$sanitizedBase.$extension"
    }

    private fun uniqueFileNameFor(context: PlaybackContext, requestedFileName: String): String {
        val base = requestedFileName.substringBeforeLast(".")
        val extension = requestedFileName.substringAfterLast(".", "mp3")
        var candidate = requestedFileName
        var index = 2
        while (fileFor(context, candidate).exists()) {
            val suffix = "-$index"
            val trimmedBase = base.take((56 - suffix.length).coerceAtLeast(1)).trimEnd(' ', '.', '_')
            candidate = "$trimmedBase$suffix.$extension"
            index++
        }
        return candidate
    }

    private fun uniqueIdleFileNameFor(context: PlaybackContext, requestedFileName: String): String {
        val base = requestedFileName.substringBeforeLast(".")
        val extension = requestedFileName.substringAfterLast(".", "mp3")
        var candidate = requestedFileName
        var index = 2
        while (fileFor(idleRelativePathFor(context, candidate)).exists()) {
            val suffix = "-$index"
            val trimmedBase = base.take((56 - suffix.length).coerceAtLeast(1)).trimEnd(' ', '.', '_')
            candidate = "$trimmedBase$suffix.$extension"
            index++
        }
        return candidate
    }

    private fun uniqueExplorationFileNameFor(context: PlaybackContext, requestedFileName: String): String {
        val base = requestedFileName.substringBeforeLast(".")
        val extension = requestedFileName.substringAfterLast(".", "mp3")
        var candidate = requestedFileName
        var index = 2
        while (fileFor(explorationRelativePathFor(context, candidate)).exists()) {
            val suffix = "-$index"
            val trimmedBase = base.take((56 - suffix.length).coerceAtLeast(1)).trimEnd(' ', '.', '_')
            candidate = "$trimmedBase$suffix.$extension"
            index++
        }
        return candidate
    }

    private fun uniqueBackgroundFileNameFor(deviceId: String, requestedFileName: String): String {
        val base = requestedFileName.substringBeforeLast(".")
        val extension = requestedFileName.substringAfterLast(".", "mp3")
        var candidate = requestedFileName
        var index = 2
        while (fileFor(backgroundRelativePathFor(deviceId, candidate)).exists()) {
            val suffix = "-$index"
            val trimmedBase = base.take((56 - suffix.length).coerceAtLeast(1)).trimEnd(' ', '.', '_')
            candidate = "$trimmedBase$suffix.$extension"
            index++
        }
        return candidate
    }

    companion object {
        private const val SOURCE_USER = "caricato"
        private const val SOURCE_ASSET = "default"
        private const val PREFS_NAME = "historia_user_audio_prefs"
        private val SUPPORTED_AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "wav", "ogg", "opus")

        fun relativePathFor(context: PlaybackContext, fileName: String): String {
            return buildPath(
                deviceId = context.deviceId,
                profile = context.profile,
                language = context.language,
                fileName = fileName
            )
        }

        private fun buildPath(deviceId: String, profile: String, language: String, fileName: String): String {
            return "${tracksRelativeDirFor(deviceId, profile, language)}/$fileName"
        }

        private fun tracksRelativeDirFor(context: PlaybackContext): String {
            return tracksRelativeDirFor(context.deviceId, context.profile, context.language)
        }

        private fun tracksRelativeDirFor(deviceId: String, profile: String, language: String): String {
            return "audio/$deviceId/$profile/$language/tracks"
        }

        private fun hiddenAssetsKeyFor(context: PlaybackContext): String {
            return "hidden_assets:${context.deviceId}:${context.profile}:${context.language}"
        }
    }

    private fun fileFor(context: PlaybackContext, fileName: String): File {
        return fileFor(relativePathFor(context, fileName))
    }

    private fun tracksDirFor(context: PlaybackContext): File {
        return File(appContext.filesDir, "audio/${context.deviceId}/${context.profile}/${context.language}/tracks")
    }

    private fun String.trackIdOrNull(): Int? {
        return substringBeforeLast(".").toIntOrNull()
    }
}
