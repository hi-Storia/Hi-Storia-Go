package it.historia.player.domain.player

import it.historia.player.domain.model.PlaybackContext

object TrackResolver {
    fun resolve(trackKey: String, ctx: PlaybackContext): String? {
        val fileName = trackKey.trim()
        if (fileName.isBlank()) return null

        val candidates = linkedSetOf(
            buildGenericPath(fileName),
            buildPath(ctx.deviceId, ctx.profile, ctx.language, fileName),
            buildPath(ctx.deviceId, PlaybackContext.DEFAULT_PROFILE, ctx.language, fileName),
            buildPath(
                ctx.deviceId,
                PlaybackContext.DEFAULT_PROFILE,
                PlaybackContext.DEFAULT_LANGUAGE,
                fileName
            ),
            buildPath(
                PlaybackContext.DEFAULT_DEVICE_ID,
                PlaybackContext.DEFAULT_PROFILE,
                PlaybackContext.DEFAULT_LANGUAGE,
                fileName
            )
        )

        return candidates.firstOrNull()
    }

    private fun buildPath(deviceId: String, profile: String, language: String, fileName: String): String {
        return "audio/$deviceId/$profile/$language/tracks/$fileName"
    }

    private fun buildGenericPath(fileName: String): String {
        return "audio/default/tracks/$fileName"
    }
}
