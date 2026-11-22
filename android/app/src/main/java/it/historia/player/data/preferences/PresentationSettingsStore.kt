package it.historia.player.data.preferences

import android.content.Context
import it.historia.player.domain.model.PlaybackContext

data class PresentationSettings(
    val customTrackKey: String? = null
)

class PresentationSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(context: PlaybackContext): PresentationSettings {
        val prefix = keyPrefix(context)
        return PresentationSettings(
            customTrackKey = prefs.getString("$prefix.custom_track_key", null)
        )
    }

    fun save(context: PlaybackContext, settings: PresentationSettings) {
        val prefix = keyPrefix(context)
        val editor = prefs.edit()
        if (settings.customTrackKey.isNullOrBlank()) {
            editor.remove("$prefix.custom_track_key")
        } else {
            editor.putString("$prefix.custom_track_key", settings.customTrackKey)
        }
        editor.apply()
    }

    /** Chiavi di questo store che appartengono al progetto [deviceId]. */
    fun projectSection(deviceId: String): ProjectPreferenceSection {
        val seriesPrefix = "series.$deviceId:"
        return ProjectPreferenceSection(PREFS_NAME, prefs) { key -> key.startsWith(seriesPrefix) }
    }

    /** Sostituisce le tracce di presentazione di tutte le serie del progetto con un unico commit. */
    fun replaceProject(deviceId: String, settings: Map<PlaybackContext, PresentationSettings>): Boolean {
        val entries = linkedMapOf<String, Any?>()
        settings.forEach { (context, presentation) ->
            require(context.deviceId == deviceId) { "Serie di un altro progetto: $context" }
            presentation.customTrackKey?.takeIf { it.isNotBlank() }?.let {
                entries["${keyPrefix(context)}.custom_track_key"] = it
            }
        }
        return projectSection(deviceId).replaceAll(entries)
    }

    private fun keyPrefix(context: PlaybackContext): String {
        return "series.${context.deviceId}:${context.profile}:${context.language}:presentation"
    }

    companion object {
        private const val PREFS_NAME = "historia_presentation_settings"
    }
}
