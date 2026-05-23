package it.historia.player.data.preferences

import android.content.Context
import it.historia.player.domain.model.PlaybackContext

class PlaybackSelectionStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadOrNull(): PlaybackContext? {
        val deviceId = prefs.getString(KEY_DEVICE_ID, null)
        val profile = prefs.getString(KEY_PROFILE, null)
        val language = prefs.getString(KEY_LANGUAGE, null)

        if (deviceId.isNullOrBlank() || profile.isNullOrBlank() || language.isNullOrBlank()) {
            return null
        }

        return PlaybackContext(
            deviceId = deviceId,
            profile = profile,
            language = language
        )
    }

    fun save(context: PlaybackContext) {
        prefs.edit()
            .putString(KEY_DEVICE_ID, context.deviceId)
            .putString(KEY_PROFILE, context.profile)
            .putString(KEY_LANGUAGE, context.language)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "historia_player_prefs"
        private const val KEY_DEVICE_ID = "selected_device_id"
        private const val KEY_PROFILE = "selected_profile"
        private const val KEY_LANGUAGE = "selected_language"
    }
}
