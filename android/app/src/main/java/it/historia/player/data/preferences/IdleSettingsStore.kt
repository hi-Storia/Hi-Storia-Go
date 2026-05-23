package it.historia.player.data.preferences

import android.content.Context
import it.historia.player.domain.model.PlaybackContext

data class IdleSettings(
    val timeoutMinutes: Int = 5
)

data class IdleTrackSettings(
    val useDefaultTrack: Boolean = true,
    val customTrackKey: String? = null
)

class IdleSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(deviceId: String): IdleSettings {
        val prefix = keyPrefix(deviceId)
        return IdleSettings(
            timeoutMinutes = prefs.getInt("$prefix.timeout_minutes", DEFAULT_TIMEOUT_MINUTES)
                .coerceIn(MIN_TIMEOUT_MINUTES, MAX_TIMEOUT_MINUTES)
        )
    }

    fun save(deviceId: String, settings: IdleSettings) {
        val prefix = keyPrefix(deviceId)
        prefs.edit()
            .putInt(
                "$prefix.timeout_minutes",
                settings.timeoutMinutes.coerceIn(MIN_TIMEOUT_MINUTES, MAX_TIMEOUT_MINUTES)
            )
            .apply()
    }

    fun loadTrackSettings(context: PlaybackContext): IdleTrackSettings {
        val prefix = trackKeyPrefix(context)
        return IdleTrackSettings(
            useDefaultTrack = prefs.getBoolean("$prefix.use_default_track", true),
            customTrackKey = prefs.getString("$prefix.custom_track_key", null)
        )
    }

    fun saveTrackSettings(context: PlaybackContext, settings: IdleTrackSettings) {
        val prefix = trackKeyPrefix(context)
        val editor = prefs.edit()
            .putBoolean("$prefix.use_default_track", settings.useDefaultTrack)
        if (settings.customTrackKey.isNullOrBlank()) {
            editor.remove("$prefix.custom_track_key")
        } else {
            editor.putString("$prefix.custom_track_key", settings.customTrackKey)
        }
        editor.apply()
    }

    companion object {
        private const val PREFS_NAME = "historia_idle_settings_prefs"
        const val MIN_TIMEOUT_MINUTES = 1
        const val MAX_TIMEOUT_MINUTES = 120
        const val DEFAULT_TIMEOUT_MINUTES = 5

        private fun keyPrefix(deviceId: String): String {
            return "device.$deviceId"
        }

        private fun trackKeyPrefix(context: PlaybackContext): String {
            return "series.${context.deviceId}:${context.profile}:${context.language}:idle"
        }
    }
}
