package it.historia.player.domain.model

data class PlaybackContext(
    val deviceId: String = DEFAULT_DEVICE_ID,
    val profile: String = DEFAULT_PROFILE,
    val language: String = DEFAULT_LANGUAGE
) {
    companion object {
        const val DEFAULT_DEVICE_ID = "default_project"
        const val DEFAULT_PROFILE = "adult"
        const val DEFAULT_LANGUAGE = "it"
    }
}
