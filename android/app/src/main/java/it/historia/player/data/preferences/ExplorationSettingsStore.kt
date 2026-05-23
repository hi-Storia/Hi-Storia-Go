package it.historia.player.data.preferences

import android.content.Context
import it.historia.player.domain.model.PlaybackContext

data class ExplorationSettings(
    val timeoutMinutes: Int = ExplorationSettingsStore.DEFAULT_TIMEOUT_MINUTES
)

data class ExplorationTrackSettings(
    val useDefaultTrack: Boolean = true,
    val customTrackKey: String? = null
)

class ExplorationSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(deviceId: String): ExplorationSettings {
        return ExplorationSettings(
            timeoutMinutes = prefs.getInt(timeoutKey(deviceId), DEFAULT_TIMEOUT_MINUTES)
                .coerceIn(MIN_TIMEOUT_MINUTES, MAX_TIMEOUT_MINUTES)
        )
    }

    fun save(deviceId: String, settings: ExplorationSettings) {
        prefs.edit()
            .putInt(
                timeoutKey(deviceId),
                settings.timeoutMinutes.coerceIn(MIN_TIMEOUT_MINUTES, MAX_TIMEOUT_MINUTES)
            )
            .apply()
    }

    fun loadTrackSettings(context: PlaybackContext): ExplorationTrackSettings {
        val prefix = trackKeyPrefix(context)
        return ExplorationTrackSettings(
            useDefaultTrack = prefs.getBoolean("$prefix.use_default_track", true),
            customTrackKey = prefs.getString("$prefix.custom_track_key", null)
        )
    }

    fun saveTrackSettings(context: PlaybackContext, settings: ExplorationTrackSettings) {
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

    private fun timeoutKey(deviceId: String): String = "exploration_timeout:$deviceId"

    private fun trackKeyPrefix(context: PlaybackContext): String {
        return "series.${context.deviceId}:${context.profile}:${context.language}:exploration"
    }

    companion object {
        private const val PREFS_NAME = "historia_exploration_settings"
        const val MIN_TIMEOUT_MINUTES = 1
        const val MAX_TIMEOUT_MINUTES = 30
        const val DEFAULT_TIMEOUT_MINUTES = 3
    }
}
