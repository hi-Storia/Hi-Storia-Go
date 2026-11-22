package it.historia.player.data.preferences

import android.content.Context
import it.historia.player.domain.model.PlaybackContext

/** Associazioni, etichette e volumi di una singola serie. */
data class SignalSeriesData(
    val mappings: Map<Int, String>,
    val labels: Map<Int, String>,
    val volumes: Map<Int, Int>
)

class SignalMappingStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(context: PlaybackContext, maxSignal: Int): Map<Int, String> {
        val parsed = parseMappings(prefs.getString(keyFor(context), null))
        return defaultMapping(maxSignal) + parsed.filterKeys { it in 1..maxSignal }
    }

    /** Solo le associazioni memorizzate esplicitamente (senza i default), su tutto l'intervallo ammesso. */
    fun loadStoredMappings(context: PlaybackContext): Map<Int, String> {
        return parseMappings(prefs.getString(keyFor(context), null))
            .filterKeys { it in 1..MAX_SIGNAL }
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
        prefs.edit()
            .putString(keyFor(context), serializeMappings(mappings, maxSignal))
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

        prefs.edit()
            .putString(labelKeyFor(context), serializeLabels(updatedLabels))
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

        prefs.edit()
            .putString(volumeKeyFor(context), serializeVolumes(updatedVolumes))
            .apply()
    }

    /** Chiavi di questo store che appartengono al progetto [deviceId]. */
    fun projectSection(deviceId: String): ProjectPreferenceSection {
        return ProjectPreferenceSection(PREFS_NAME, prefs) { key -> key.startsWith("$deviceId:") }
    }

    /**
     * Sostituisce integralmente associazioni, etichette e volumi di tutte le serie del progetto
     * con un unico commit sincrono; le chiavi di serie non elencate vengono eliminate.
     */
    fun replaceProject(deviceId: String, series: Map<PlaybackContext, SignalSeriesData>): Boolean {
        val entries = linkedMapOf<String, Any?>()
        series.forEach { (context, data) ->
            require(context.deviceId == deviceId) { "Serie di un altro progetto: $context" }
            serializeMappings(data.mappings, MAX_SIGNAL)
                .takeIf { it.isNotEmpty() }
                ?.let { entries[keyFor(context)] = it }
            serializeLabels(data.labels.filterKeys { it in 1..MAX_SIGNAL })
                .takeIf { it.isNotEmpty() }
                ?.let { entries[labelKeyFor(context)] = it }
            serializeVolumes(data.volumes.filterKeys { it in 1..MAX_SIGNAL })
                .takeIf { it.isNotEmpty() }
                ?.let { entries[volumeKeyFor(context)] = it }
        }
        return projectSection(deviceId).replaceAll(entries)
    }

    companion object {
        private const val PREFS_NAME = "historia_signal_mapping_prefs"
        const val MAX_SIGNAL = ProjectSettingsStore.MAX_TRACK_COUNT

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

        private fun parseMappings(stored: String?): Map<Int, String> {
            return stored
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
        }

        private fun serializeMappings(mappings: Map<Int, String>, maxSignal: Int): String {
            return mappings
                .filterKeys { it in 1..maxSignal }
                .mapValues { (_, track) -> normalizeTrackKey(track) }
                .filterValues { it.isNotBlank() }
                .toSortedMap()
                .entries
                .joinToString(",") { "${it.key}:${it.value}" }
        }

        private fun serializeLabels(labels: Map<Int, String>): String {
            return labels
                .mapValues { (_, label) -> label.replace(LINE_BREAKS, " ").trim() }
                .filterValues { it.isNotBlank() }
                .toSortedMap()
                .entries
                .joinToString("\n") { "${it.key}:${it.value}" }
        }

        private fun serializeVolumes(volumes: Map<Int, Int>): String {
            return volumes
                .mapValues { (_, volume) -> volume.coerceIn(MIN_VOLUME_PERCENT, MAX_VOLUME_PERCENT) }
                .filterValues { it != MAX_VOLUME_PERCENT }
                .toSortedMap()
                .entries
                .joinToString(",") { "${it.key}:${it.value}" }
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
        private val LINE_BREAKS = Regex("[\\r\\n]+")
    }
}
