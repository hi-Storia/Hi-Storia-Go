package it.historia.player.data.preferences

import android.content.Context

data class BackgroundMusicSettings(
    val enabled: Boolean = false,
    val useDefaultTrack: Boolean = true,
    val customTrackKey: String? = null,
    val onlyDuringTracks: Boolean = false,
    val fullVolumePercent: Int = 100,
    val duckedVolumePercent: Int = 8
)

class BackgroundMusicSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(deviceId: String): BackgroundMusicSettings {
        return BackgroundMusicSettings(
            enabled = prefs.getBoolean(enabledKey(deviceId), false),
            useDefaultTrack = prefs.getBoolean(useDefaultKey(deviceId), true),
            customTrackKey = prefs.getString(customTrackKey(deviceId), null),
            onlyDuringTracks = prefs.getBoolean(onlyDuringTracksKey(deviceId), false),
            fullVolumePercent = prefs.getInt(fullVolumeKey(deviceId), 100).coerceIn(0, 100),
            duckedVolumePercent = prefs.getInt(duckedVolumeKey(deviceId), 8).coerceIn(
                0,
                prefs.getInt(fullVolumeKey(deviceId), 100).coerceIn(0, 100)
            )
        )
    }

    fun save(deviceId: String, settings: BackgroundMusicSettings) {
        val fullVolume = settings.fullVolumePercent.coerceIn(0, 100)
        val duckedVolume = settings.duckedVolumePercent.coerceIn(0, fullVolume)
        prefs.edit()
            .putBoolean(enabledKey(deviceId), settings.enabled)
            .putBoolean(useDefaultKey(deviceId), settings.useDefaultTrack)
            .putString(customTrackKey(deviceId), settings.customTrackKey)
            .putBoolean(onlyDuringTracksKey(deviceId), settings.onlyDuringTracks)
            .putInt(fullVolumeKey(deviceId), fullVolume)
            .putInt(duckedVolumeKey(deviceId), duckedVolume)
            .apply()
    }

    private fun enabledKey(deviceId: String): String = "background_enabled:$deviceId"

    private fun useDefaultKey(deviceId: String): String = "background_use_default:$deviceId"

    private fun customTrackKey(deviceId: String): String = "background_custom_track:$deviceId"

    private fun onlyDuringTracksKey(deviceId: String): String = "background_only_during_tracks:$deviceId"

    private fun fullVolumeKey(deviceId: String): String = "background_full_volume:$deviceId"

    private fun duckedVolumeKey(deviceId: String): String = "background_ducked_volume:$deviceId"

    companion object {
        private const val PREFS_NAME = "historia_background_music_settings"
    }
}
