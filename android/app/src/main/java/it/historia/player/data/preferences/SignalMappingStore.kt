package it.historia.player.data.preferences

import android.content.Context
import it.historia.player.domain.model.PlaybackContext

class SignalMappingStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(context: PlaybackContext, maxSignal: Int): Map<Int, String> {
        val stored = prefs.getString(keyFor(context), null)
        val parsed = stored
            ?.split(",")
            .orEmpty()
            .mapNotNull { entry ->
                val signal = entry.substringBefore(":", missingDelimiterValue = "").toIntOrNull()
                val track = entry.substringAfter(":", missingDelimiterValue = "")
                    .takeIf { it.isNotBlank() }
                    ?.let { normalizeTrackKey(it) }
                if (signal == null || track == null) null else signal to track
            }
            .toMap()

        return defaultMapping(maxSignal) + parsed.filterKeys { it in 1..maxSignal }
    }

    fun loadLabels(context: PlaybackContext, maxTrack: Int): Map<Int, String> {
        val stored = prefs.getString(labelKeyFor(context), null)
        return stored
            ?.split("\n")
            .orEmpty()
            .mapNotNull { entry ->
                val signal = entry.substringBefore(":", missingDelimiterValue = "").toIntOrNull()
                val label = entry.substringAfter(":", missingDelimiterValue = "").trim()
                if (signal == null || signal !in 1..maxTrack || label.isBlank()) {
                    null
                } else {
                    signal to label
                }
            }
            .toMap()
    }

    fun loadVolumes(context: PlaybackContext, maxTrack: Int): Map<Int, Int> {
        val stored = prefs.getString(volumeKeyFor(context), null)
        return stored
            ?.split(",")
            .orEmpty()
            .mapNotNull { entry ->
                val signal = entry.substringBefore(":", missingDelimiterValue = "").toIntOrNull()
                val volume = entry.substringAfter(":", missingDelimiterValue = "").toIntOrNull()
                if (signal == null || signal !in 1..maxTrack || volume == null) {
                    null
                } else {
                    signal to volume.coerceIn(MIN_VOLUME_PERCENT, MAX_VOLUME_PERCENT)
                }
            }
            .toMap()
    }

    fun save(context: PlaybackContext, mappings: Map<Int, String>, maxSignal: Int) {
        val sanitized = mappings
            .filterKeys { it in 1..maxSignal }
            .mapValues { (_, track) -> normalizeTrackKey(track) }
            .filterValues { it.isNotBlank() }
            .toSortedMap()
            .entries
            .joinToString(",") { "${it.key}:${it.value}" }

        prefs.edit()
            .putString(keyFor(context), sanitized)
            .apply()
    }

    fun saveLabel(context: PlaybackContext, signalIndex: Int, label: String, maxTrack: Int) {
        if (signalIndex !in 1..maxTrack) return

        val updatedLabels = loadLabels(context, maxTrack).toMutableMap()
        val sanitizedLabel = label.trim()
        if (sanitizedLabel.isBlank()) {
            updatedLabels.remove(signalIndex)
        } else {
            updatedLabels[signalIndex] = sanitizedLabel
        }

        val serialized = updatedLabels
            .toSortedMap()
            .entries
            .joinToString("\n") { "${it.key}:${it.value}" }

        prefs.edit()
            .putString(labelKeyFor(context), serialized)
            .apply()
    }

    fun saveVolume(context: PlaybackContext, signalIndex: Int, volumePercent: Int, maxTrack: Int) {
        if (signalIndex !in 1..maxTrack) return

        val updatedVolumes = loadVolumes(context, maxTrack).toMutableMap()
        val sanitizedVolume = volumePercent.coerceIn(MIN_VOLUME_PERCENT, MAX_VOLUME_PERCENT)
        if (sanitizedVolume == MAX_VOLUME_PERCENT) {
            updatedVolumes.remove(signalIndex)
        } else {
            updatedVolumes[signalIndex] = sanitizedVolume
        }

        val serialized = updatedVolumes
            .toSortedMap()
            .entries
            .joinToString(",") { "${it.key}:${it.value}" }

        prefs.edit()
            .putString(volumeKeyFor(context), serialized)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "historia_signal_mapping_prefs"

        fun defaultMapping(maxSignal: Int): Map<Int, String> {
            return (1..maxSignal.coerceAtLeast(1)).associateWith { DEFAULT_TRACK_KEY }
        }

        private fun normalizeTrackKey(track: String): String {
            val trimmed = track.trim()
            val numericTrack = trimmed.toIntOrNull()
            return if (numericTrack != null && numericTrack > 0) {
                "%02d.mp3".format(numericTrack)
            } else {
                trimmed
            }
        }

        private fun keyFor(context: PlaybackContext): String {
            return "${context.deviceId}:${context.profile}:${context.language}"
        }

        private fun labelKeyFor(context: PlaybackContext): String {
            return "${keyFor(context)}:labels"
        }

        private fun volumeKeyFor(context: PlaybackContext): String {
            return "${keyFor(context)}:volumes"
        }

        private const val DEFAULT_TRACK_KEY = "00.mp3"
        private const val MIN_VOLUME_PERCENT = 10
        private const val MAX_VOLUME_PERCENT = 100
    }
}
