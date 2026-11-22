package it.historia.player.data.preferences

import android.content.Context

data class BackgroundMusicSettings(
    val enabled: Boolean = false,
    val useDefaultTrack: Boolean = true,
    val customTrackKey: String? = null,
    val onlyDuringTracks: Boolean = false,
    val fullVolumePercent: Float = 100f,
    val duckedVolumePercent: Float = 8f
)

class BackgroundMusicSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(deviceId: String): BackgroundMusicSettings {
        return BackgroundMusicSettings(
            enabled = prefs.getBoolean(enabledKey(deviceId), false),
            useDefaultTrack = prefs.getBoolean(useDefaultKey(deviceId), true),
            customTrackKey = prefs.getString(customTrackKey(deviceId), null),
            onlyDuringTracks = prefs.getBoolean(onlyDuringTracksKey(deviceId), false),
            fullVolumePercent = readVolume(fullVolumeKey(deviceId), 100f).coerceIn(0f, 100f),
            duckedVolumePercent = readVolume(duckedVolumeKey(deviceId), 8f).coerceIn(
                0f,
                readVolume(fullVolumeKey(deviceId), 100f).coerceIn(0f, 100f)
            )
        )
    }

    fun save(deviceId: String, settings: BackgroundMusicSettings) {
        val fullVolume = settings.fullVolumePercent.coerceIn(0f, 100f)
        val duckedVolume = settings.duckedVolumePercent.coerceIn(0f, fullVolume)
        prefs.edit()
            .putBoolean(enabledKey(deviceId), settings.enabled)
            .putBoolean(useDefaultKey(deviceId), settings.useDefaultTrack)
            .putString(customTrackKey(deviceId), settings.customTrackKey)
            .putBoolean(onlyDuringTracksKey(deviceId), settings.onlyDuringTracks)
            .putFloat(fullVolumeKey(deviceId), fullVolume)
            .putFloat(duckedVolumeKey(deviceId), duckedVolume)
            .apply()
    }

    /** Chiavi di questo store che appartengono al progetto [deviceId]. */
    fun projectSection(deviceId: String): ProjectPreferenceSection {
        val keys = setOf(
            enabledKey(deviceId),
            useDefaultKey(deviceId),
            customTrackKey(deviceId),
            onlyDuringTracksKey(deviceId),
            fullVolumeKey(deviceId),
            duckedVolumeKey(deviceId)
        )
        return ProjectPreferenceSection(PREFS_NAME, prefs) { key -> key in keys }
    }

    /** Sostituisce le impostazioni della musica di sottofondo con un unico commit sincrono. */
    fun replaceProject(deviceId: String, settings: BackgroundMusicSettings): Boolean {
        val fullVolume = settings.fullVolumePercent.coerceIn(0f, 100f)
        val duckedVolume = settings.duckedVolumePercent.coerceIn(0f, fullVolume)
        return projectSection(deviceId).replaceAll(
            linkedMapOf(
                enabledKey(deviceId) to settings.enabled,
                useDefaultKey(deviceId) to settings.useDefaultTrack,
                customTrackKey(deviceId) to settings.customTrackKey?.takeIf { it.isNotBlank() },
                onlyDuringTracksKey(deviceId) to settings.onlyDuringTracks,
                fullVolumeKey(deviceId) to fullVolume,
                duckedVolumeKey(deviceId) to duckedVolume
            )
        )
    }

    private fun readVolume(key: String, defaultValue: Float): Float {
        return when (val stored = prefs.all[key]) {
            is Float -> stored
            is Int -> stored.toFloat()
            is Long -> stored.toFloat()
            is String -> stored.toFloatOrNull() ?: defaultValue
            else -> defaultValue
        }
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
