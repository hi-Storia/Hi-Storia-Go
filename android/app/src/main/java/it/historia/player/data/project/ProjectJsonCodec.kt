package it.historia.player.data.project

import it.historia.player.data.preferences.BackgroundMusicSettings
import it.historia.player.data.preferences.ExplorationSettings
import it.historia.player.data.preferences.ExplorationSettingsStore
import it.historia.player.data.preferences.ExplorationTrackSettings
import it.historia.player.data.preferences.IdleSettings
import it.historia.player.data.preferences.IdleSettingsStore
import it.historia.player.data.preferences.IdleTrackSettings
import it.historia.player.data.preferences.PresentationSettings
import it.historia.player.data.preferences.ProjectSettings
import it.historia.player.data.preferences.ProjectSettingsStore
import it.historia.player.data.project.ProjectArchiveException.Reason
import it.historia.player.domain.player.DeviceDefinition
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Serializzazione esplicita e versionata di manifest e progetto. Ogni campo viene letto con
 * controllo di tipo e intervallo: nessun valore dell'archivio raggiunge gli store senza validazione.
 */
object ProjectJsonCodec {
    private const val MIN_VOLUME_PERCENT = 10
    private const val MAX_VOLUME_PERCENT = 100
    private val SHA256_REGEX = Regex("^[0-9a-f]{64}$")

    // ---------------------------------------------------------------- manifest

    fun encodeManifest(manifest: ProjectArchiveManifest, snapshot: ProjectSnapshot): String {
        val json = JSONObject()
            .put("format", ProjectArchiveFormat.FORMAT_ID)
            .put("schemaVersion", manifest.schemaVersion)
            .put("createdAt", manifest.createdAt)
            .put(
                "generator",
                JSONObject()
                    .put("app", manifest.generator.appName)
                    .put("applicationId", manifest.generator.applicationId)
                    .put("versionName", manifest.generator.versionName)
                    .put("versionCode", manifest.generator.versionCode)
            )
            .put(
                "project",
                JSONObject()
                    .put("deviceId", manifest.deviceId)
                    .put("name", manifest.projectName)
                    .put("school", manifest.schoolName)
                    .put("trackCount", snapshot.settings.trackCount)
                    .put("profileCount", snapshot.series.map { it.profileId }.distinct().size)
                    .put("seriesCount", snapshot.series.size)
                    .put("enabledSeriesCount", snapshot.settings.enabledSeries.size)
            )
            .put("audioFileCount", manifest.files.size)
            .put("totalAudioBytes", manifest.totalAudioBytes)
            .put(
                "files",
                JSONArray().apply {
                    manifest.files.forEach { file ->
                        put(
                            JSONObject()
                                .put("path", file.path)
                                .put("size", file.size)
                                .put("sha256", file.sha256)
                        )
                    }
                }
            )
            .put("assetDependencies", JSONArray(manifest.assetDependencies.sorted()))
        return json.toString(2)
    }

    fun decodeManifest(text: String): ProjectArchiveManifest = guarded {
        val json = parseObject(text, "manifest.json")
        val format = json.reqString("format")
        if (format != ProjectArchiveFormat.FORMAT_ID) {
            invalid("Il file non è un progetto Hi-Storia Go (formato \"$format\").")
        }
        val schemaVersion = json.reqInt("schemaVersion")
        if (schemaVersion > ProjectArchiveFormat.SCHEMA_VERSION) {
            archiveError(
                Reason.UNSUPPORTED_VERSION,
                "Il progetto usa il formato versione $schemaVersion, ma questa app supporta fino alla " +
                    "versione ${ProjectArchiveFormat.SCHEMA_VERSION}. Aggiorna Hi-Storia Go e riprova."
            )
        }
        if (schemaVersion < 1) invalid("Versione del formato non valida ($schemaVersion).")

        val generatorJson = json.reqObject("generator")
        val generator = ArchiveGenerator(
            appName = generatorJson.optText("app", 100).orEmpty(),
            applicationId = generatorJson.optText("applicationId", 200).orEmpty(),
            versionName = generatorJson.optText("versionName", 100).orEmpty(),
            versionCode = generatorJson.optLong("versionCode", 0L)
        )
        val project = json.reqObject("project")
        val deviceId = project.reqId("deviceId")

        val filesJson = json.reqArray("files")
        if (filesJson.length() > ProjectArchiveFormat.MAX_ENTRY_COUNT - 2) {
            archiveError(Reason.LIMIT_EXCEEDED, "L'archivio contiene troppi file (massimo ${ProjectArchiveFormat.MAX_ENTRY_COUNT - 2}).")
        }
        val seenPaths = mutableSetOf<String>()
        val files = (0 until filesJson.length()).map { index ->
            val item = filesJson.reqObjectAt(index)
            val path = item.reqString("path")
            if (ProjectArchiveFormat.parseArchivePath(path) == null) {
                archiveError(Reason.UNSAFE_PATH, "Percorso non valido nel manifest: \"${path.take(80)}\".")
            }
            if (!seenPaths.add(path)) invalid("File duplicato nel manifest: $path.")
            val size = item.reqLong("size")
            if (size < 0) invalid("Dimensione non valida per $path.")
            if (size > ProjectArchiveFormat.MAX_AUDIO_FILE_BYTES) {
                archiveError(Reason.LIMIT_EXCEEDED, "Il file $path supera la dimensione massima consentita.")
            }
            val sha256 = item.reqString("sha256").lowercase()
            if (!SHA256_REGEX.matches(sha256)) invalid("Impronta SHA-256 non valida per $path.")
            ArchiveFileEntry(path, size, sha256)
        }
        if (files.sumOf { it.size } > ProjectArchiveFormat.MAX_TOTAL_AUDIO_BYTES) {
            archiveError(Reason.LIMIT_EXCEEDED, "Il contenuto audio supera la dimensione massima consentita.")
        }

        val assetsJson = json.optJSONArray("assetDependencies") ?: JSONArray()
        val assetDependencies = (0 until assetsJson.length()).map { index ->
            val value = assetsJson.opt(index) as? String ?: invalid("Dipendenza asset non valida.")
            if (!ProjectArchiveFormat.isSafeEntryName(value) || !value.startsWith("audio/")) {
                invalid("Dipendenza asset non valida: ${value.take(80)}.")
            }
            value
        }

        ProjectArchiveManifest(
            schemaVersion = schemaVersion,
            createdAt = json.optText("createdAt", 64).orEmpty(),
            generator = generator,
            deviceId = deviceId,
            projectName = project.optText("name", ProjectArchiveFormat.MAX_PROJECT_TEXT_LENGTH).orEmpty(),
            schoolName = project.optText("school", ProjectArchiveFormat.MAX_PROJECT_TEXT_LENGTH).orEmpty(),
            files = files,
            assetDependencies = assetDependencies
        )
    }

    // ---------------------------------------------------------------- project

    fun encodeProject(snapshot: ProjectSnapshot, device: DeviceDefinition): String {
        val settings = snapshot.settings
        val builtInProfileIds = device.profiles.map { it.profileId }
        val profileIds = builtInProfileIds + settings.customProfiles.sorted()
        val profiles = JSONArray()
        profileIds.forEach { profileId ->
            profiles.put(
                JSONObject()
                    .put("id", profileId)
                    .put("label", settings.profileLabels[profileId] ?: profileId)
                    .put("custom", settings.customProfiles.contains(profileId))
                    .put(
                        "customLanguages",
                        JSONArray(settings.customLanguagesByProfile[profileId].orEmpty().sorted())
                    )
            )
        }
        val languages = JSONObject()
        settings.languageLabels.toSortedMap().forEach { (id, label) -> languages.put(id, label) }

        val series = JSONArray()
        snapshot.series.forEach { item ->
            val signals = JSONArray()
            val signalIndexes = (item.trackMappings.keys + item.labels.keys + item.volumes.keys).toSortedSet()
            signalIndexes.forEach { signal ->
                val entry = JSONObject().put("signal", signal)
                item.trackMappings[signal]?.let { entry.put("track", it) }
                item.labels[signal]?.let { entry.put("label", it) }
                item.volumes[signal]?.let { entry.put("volume", it) }
                signals.put(entry)
            }
            series.put(
                JSONObject()
                    .put("profileId", item.profileId)
                    .put("language", item.language)
                    .put(
                        "enabled",
                        settings.enabledSeries.contains(ProjectSettingsStore.seriesKey(item.profileId, item.language))
                    )
                    .put("signals", signals)
                    .put("hiddenDefaultTracks", JSONArray(item.hiddenDefaultTracks.sorted()))
                    .put(
                        "idle",
                        JSONObject()
                            .put("useDefaultTrack", item.idleTrack.useDefaultTrack)
                            .put("track", item.idleTrack.customTrackKey ?: JSONObject.NULL)
                    )
                    .put(
                        "exploration",
                        JSONObject()
                            .put("useDefaultTrack", item.explorationTrack.useDefaultTrack)
                            .put("track", item.explorationTrack.customTrackKey ?: JSONObject.NULL)
                    )
                    .put(
                        "presentation",
                        JSONObject().put("track", item.presentation.customTrackKey ?: JSONObject.NULL)
                    )
            )
        }

        val background = snapshot.backgroundMusic
        val json = JSONObject()
            .put("schemaVersion", ProjectArchiveFormat.SCHEMA_VERSION)
            .put("deviceId", snapshot.deviceId)
            .put("name", settings.culturalAssetName)
            .put("school", settings.schoolName)
            .put("trackCount", settings.trackCount)
            .put("profiles", profiles)
            .put("languages", languages)
            .put("series", series)
            .put("idle", JSONObject().put("timeoutMinutes", snapshot.idle.timeoutMinutes))
            .put("exploration", JSONObject().put("timeoutMinutes", snapshot.exploration.timeoutMinutes))
            .put(
                "backgroundMusic",
                JSONObject()
                    .put("enabled", background.enabled)
                    .put("useDefaultTrack", background.useDefaultTrack)
                    .put("track", background.customTrackKey ?: JSONObject.NULL)
                    .put("onlyDuringTracks", background.onlyDuringTracks)
                    .put("fullVolumePercent", background.fullVolumePercent.toDouble())
                    .put("duckedVolumePercent", background.duckedVolumePercent.toDouble())
            )
        return json.toString(2)
    }

    /**
     * Decodifica e valida `project.json`. [deviceLookup] restituisce la definizione del dispositivo
     * di questa app: tipologie e lingue predefinite devono esistere anche qui.
     */
    fun decodeProject(text: String, deviceLookup: (String) -> DeviceDefinition?): ProjectSnapshot = guarded {
        val json = parseObject(text, "project.json")
        val deviceId = json.reqId("deviceId")
        val device = deviceLookup(deviceId) ?: archiveError(
            Reason.INCOMPATIBLE_PROJECT,
            "Il progetto appartiene a una configurazione (\"$deviceId\") non disponibile in questa versione dell'app."
        )

        val trackCount = json.reqInt("trackCount")
        if (trackCount !in ProjectSettingsStore.MIN_TRACK_COUNT..ProjectSettingsStore.MAX_TRACK_COUNT) {
            invalid("Numero di attivatori non valido ($trackCount).")
        }

        // Tipologie.
        val builtInProfiles = device.profiles.associateBy { it.profileId }
        val profilesJson = json.reqArray("profiles")
        if (profilesJson.length() == 0) invalid("Il progetto non contiene tipologie.")
        if (profilesJson.length() > 200) archiveError(Reason.LIMIT_EXCEEDED, "Il progetto contiene troppe tipologie.")
        val profileLabels = linkedMapOf<String, String>()
        val customProfiles = mutableSetOf<String>()
        val customLanguagesByProfile = linkedMapOf<String, Set<String>>()
        for (index in 0 until profilesJson.length()) {
            val item = profilesJson.reqObjectAt(index)
            val id = item.reqId("id")
            if (profileLabels.containsKey(id)) invalid("Tipologia duplicata: $id.")
            val label = item.reqText("label", ProjectArchiveFormat.MAX_LABEL_LENGTH)
            val custom = item.reqBoolean("custom")
            val customLanguages = item.reqIdArray("customLanguages")
            val builtIn = builtInProfiles[id]
            if (custom) {
                if (builtIn != null) invalid("La tipologia personalizzata \"$id\" coincide con una predefinita.")
                if (customLanguages.isEmpty()) invalid("La tipologia \"$label\" non ha lingue.")
                customProfiles += id
            } else {
                if (builtIn == null) {
                    archiveError(
                        Reason.INCOMPATIBLE_PROJECT,
                        "La tipologia predefinita \"$id\" non esiste in questa versione dell'app."
                    )
                }
                if (customLanguages.any { it in builtIn.languages }) {
                    invalid("La tipologia \"$label\" ripete una lingua predefinita.")
                }
            }
            profileLabels[id] = label
            if (customLanguages.isNotEmpty()) customLanguagesByProfile[id] = customLanguages
        }

        // Serie attese, nello stesso ordine usato dall'app.
        val partialSettings = ProjectSettings(
            culturalAssetName = "",
            schoolName = "",
            trackCount = trackCount,
            profileLabels = profileLabels,
            languageLabels = emptyMap(),
            customProfiles = customProfiles,
            customLanguagesByProfile = customLanguagesByProfile,
            enabledSeries = emptySet()
        )
        val expectedSeries = ProjectSettingsStore.projectSeries(device, partialSettings)
        val referencedLanguages = expectedSeries.map { it.second }.toSet()

        // Etichette lingue (solo quelle usate dal progetto).
        val languagesJson = json.reqObject("languages")
        val languageLabels = linkedMapOf<String, String>()
        languagesJson.keys().forEach { languageId ->
            if (!ProjectArchiveFormat.isValidId(languageId)) invalid("Identificativo lingua non valido.")
            val label = languagesJson.opt(languageId) as? String ?: invalid("Etichetta lingua non valida per $languageId.")
            if (label.length > ProjectArchiveFormat.MAX_LABEL_LENGTH) invalid("Etichetta lingua troppo lunga.")
            if (languageId in referencedLanguages) languageLabels[languageId] = label
        }
        referencedLanguages.forEach { languageId ->
            languageLabels.putIfAbsent(languageId, ProjectSettingsStore.defaultLanguageLabel(languageId))
        }

        // Serie.
        val seriesJson = json.reqArray("series")
        if (seriesJson.length() > expectedSeries.size) invalid("Il progetto contiene serie non previste.")
        val decodedSeries = linkedMapOf<Pair<String, String>, SeriesSnapshot>()
        val enabledSeries = mutableSetOf<String>()
        for (index in 0 until seriesJson.length()) {
            val item = seriesJson.reqObjectAt(index)
            val profileId = item.reqId("profileId")
            val language = item.reqId("language")
            val key = profileId to language
            if (key !in expectedSeries) invalid("Serie non prevista dal progetto: $profileId/$language.")
            if (decodedSeries.containsKey(key)) invalid("Serie duplicata: $profileId/$language.")
            if (item.reqBoolean("enabled")) enabledSeries += ProjectSettingsStore.seriesKey(profileId, language)
            decodedSeries[key] = decodeSeries(item, profileId, language)
        }
        if (enabledSeries.isEmpty()) invalid("Il progetto deve avere almeno una serie attiva.")
        val series = expectedSeries.map { key ->
            decodedSeries[key] ?: emptySeries(key.first, key.second)
        }

        val idleJson = json.reqObject("idle")
        val idleTimeout = idleJson.reqInt("timeoutMinutes")
        if (idleTimeout !in IdleSettingsStore.MIN_TIMEOUT_MINUTES..IdleSettingsStore.MAX_TIMEOUT_MINUTES) {
            invalid("Timeout idle non valido ($idleTimeout).")
        }
        val explorationJson = json.reqObject("exploration")
        val explorationTimeout = explorationJson.reqInt("timeoutMinutes")
        if (explorationTimeout !in ExplorationSettingsStore.MIN_TIMEOUT_MINUTES..ExplorationSettingsStore.MAX_TIMEOUT_MINUTES) {
            invalid("Timeout esplorazione non valido ($explorationTimeout).")
        }

        val backgroundJson = json.reqObject("backgroundMusic")
        val fullVolume = backgroundJson.reqPercent("fullVolumePercent")
        val duckedVolume = backgroundJson.reqPercent("duckedVolumePercent")
        if (duckedVolume > fullVolume) invalid("Il volume attenuato supera il volume pieno.")
        val backgroundMusic = BackgroundMusicSettings(
            enabled = backgroundJson.reqBoolean("enabled"),
            useDefaultTrack = backgroundJson.reqBoolean("useDefaultTrack"),
            customTrackKey = backgroundJson.optTrackKey("track"),
            onlyDuringTracks = backgroundJson.reqBoolean("onlyDuringTracks"),
            fullVolumePercent = fullVolume,
            duckedVolumePercent = duckedVolume
        )

        ProjectSnapshot(
            deviceId = deviceId,
            settings = ProjectSettings(
                culturalAssetName = json.reqText("name", ProjectArchiveFormat.MAX_PROJECT_TEXT_LENGTH),
                schoolName = json.reqText("school", ProjectArchiveFormat.MAX_PROJECT_TEXT_LENGTH),
                trackCount = trackCount,
                profileLabels = profileLabels,
                languageLabels = languageLabels,
                customProfiles = customProfiles,
                customLanguagesByProfile = customLanguagesByProfile,
                enabledSeries = enabledSeries
            ),
            idle = IdleSettings(timeoutMinutes = idleTimeout),
            exploration = ExplorationSettings(timeoutMinutes = explorationTimeout),
            backgroundMusic = backgroundMusic,
            series = series
        )
    }

    private fun decodeSeries(item: JSONObject, profileId: String, language: String): SeriesSnapshot {
        val signalsJson = item.reqArray("signals")
        if (signalsJson.length() > ProjectArchiveFormat.MAX_SIGNAL) invalid("Troppi attivatori nella serie $profileId/$language.")
        val mappings = sortedMapOf<Int, String>()
        val labels = sortedMapOf<Int, String>()
        val volumes = sortedMapOf<Int, Int>()
        val seenSignals = mutableSetOf<Int>()
        for (index in 0 until signalsJson.length()) {
            val signalJson = signalsJson.reqObjectAt(index)
            val signal = signalJson.reqInt("signal")
            if (signal !in 1..ProjectArchiveFormat.MAX_SIGNAL) invalid("Attivatore non valido ($signal).")
            if (!seenSignals.add(signal)) invalid("Attivatore duplicato (T$signal) in $profileId/$language.")
            signalJson.optTrackKey("track")?.let { mappings[signal] = it }
            signalJson.optText("label", ProjectArchiveFormat.MAX_LABEL_LENGTH)
                ?.replace(Regex("[\\r\\n]+"), " ")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { labels[signal] = it }
            if (signalJson.has("volume") && !signalJson.isNull("volume")) {
                val volume = signalJson.reqInt("volume")
                if (volume !in MIN_VOLUME_PERCENT..MAX_VOLUME_PERCENT) invalid("Volume non valido per T$signal.")
                volumes[signal] = volume
            }
        }

        val hiddenJson = item.reqArray("hiddenDefaultTracks")
        val hidden = (0 until hiddenJson.length()).map { index ->
            val value = hiddenJson.opt(index) as? String ?: invalid("Traccia nascosta non valida.")
            if (!ProjectArchiveFormat.isValidTrackKey(value)) invalid("Traccia nascosta non valida.")
            value
        }.toSet()

        val idleJson = item.reqObject("idle")
        val explorationJson = item.reqObject("exploration")
        val presentationJson = item.reqObject("presentation")
        return SeriesSnapshot(
            profileId = profileId,
            language = language,
            trackMappings = mappings,
            labels = labels,
            volumes = volumes,
            hiddenDefaultTracks = hidden,
            idleTrack = IdleTrackSettings(
                useDefaultTrack = idleJson.reqBoolean("useDefaultTrack"),
                customTrackKey = idleJson.optTrackKey("track")
            ),
            explorationTrack = ExplorationTrackSettings(
                useDefaultTrack = explorationJson.reqBoolean("useDefaultTrack"),
                customTrackKey = explorationJson.optTrackKey("track")
            ),
            presentation = PresentationSettings(customTrackKey = presentationJson.optTrackKey("track"))
        )
    }

    fun emptySeries(profileId: String, language: String) = SeriesSnapshot(
        profileId = profileId,
        language = language,
        trackMappings = emptyMap(),
        labels = emptyMap(),
        volumes = emptyMap(),
        hiddenDefaultTracks = emptySet(),
        idleTrack = IdleTrackSettings(),
        explorationTrack = ExplorationTrackSettings(),
        presentation = PresentationSettings()
    )

    // ---------------------------------------------------------------- helpers

    private inline fun <T> guarded(block: () -> T): T {
        return try {
            block()
        } catch (error: JSONException) {
            invalid("Contenuto JSON non valido: ${error.message.orEmpty().take(120)}")
        } catch (error: ClassCastException) {
            invalid("Contenuto JSON non valido.")
        }
    }

    private fun invalid(message: String): Nothing = archiveError(Reason.INVALID_MANIFEST, message)

    private fun parseObject(text: String, name: String): JSONObject {
        val trimmed = text.trim()
        if (!trimmed.startsWith("{")) invalid("$name non è un oggetto JSON.")
        return JSONObject(trimmed)
    }

    private fun JSONObject.reqValue(key: String): Any {
        if (!has(key) || isNull(key)) invalid("Campo obbligatorio mancante: $key.")
        return get(key)
    }

    private fun JSONObject.reqString(key: String): String {
        return reqValue(key) as? String ?: invalid("Il campo $key deve essere un testo.")
    }

    private fun JSONObject.reqText(key: String, maxLength: Int): String {
        val value = reqString(key)
        if (value.length > maxLength) invalid("Il campo $key è troppo lungo.")
        return value
    }

    private fun JSONObject.optText(key: String, maxLength: Int): String? {
        if (!has(key) || isNull(key)) return null
        val value = get(key) as? String ?: invalid("Il campo $key deve essere un testo.")
        if (value.length > maxLength) invalid("Il campo $key è troppo lungo.")
        return value
    }

    private fun JSONObject.reqId(key: String): String {
        val value = reqString(key)
        if (!ProjectArchiveFormat.isValidId(value)) invalid("Identificativo non valido nel campo $key.")
        return value
    }

    private fun JSONObject.optTrackKey(key: String): String? {
        val value = optText(key, ProjectArchiveFormat.MAX_FILE_NAME_LENGTH) ?: return null
        if (!ProjectArchiveFormat.isValidTrackKey(value)) invalid("Nome traccia non valido nel campo $key.")
        return value
    }

    private fun JSONObject.reqBoolean(key: String): Boolean {
        return reqValue(key) as? Boolean ?: invalid("Il campo $key deve essere vero/falso.")
    }

    private fun JSONObject.reqLong(key: String): Long {
        val value = reqValue(key) as? Number ?: invalid("Il campo $key deve essere un numero.")
        val asLong = value.toLong()
        if (value.toDouble() != asLong.toDouble()) invalid("Il campo $key deve essere un numero intero.")
        return asLong
    }

    private fun JSONObject.reqInt(key: String): Int {
        val value = reqLong(key)
        if (value !in Int.MIN_VALUE..Int.MAX_VALUE) invalid("Il campo $key è fuori intervallo.")
        return value.toInt()
    }

    private fun JSONObject.reqPercent(key: String): Float {
        val value = reqValue(key) as? Number ?: invalid("Il campo $key deve essere un numero.")
        val asDouble = value.toDouble()
        if (asDouble.isNaN() || asDouble < 0.0 || asDouble > 100.0) invalid("Il campo $key è fuori intervallo.")
        return asDouble.toFloat()
    }

    private fun JSONObject.reqObject(key: String): JSONObject {
        return reqValue(key) as? JSONObject ?: invalid("Il campo $key deve essere un oggetto.")
    }

    private fun JSONObject.reqArray(key: String): JSONArray {
        return reqValue(key) as? JSONArray ?: invalid("Il campo $key deve essere un elenco.")
    }

    private fun JSONObject.reqIdArray(key: String): Set<String> {
        val array = reqArray(key)
        val values = linkedSetOf<String>()
        for (index in 0 until array.length()) {
            val value = array.opt(index) as? String ?: invalid("Elenco $key non valido.")
            if (!ProjectArchiveFormat.isValidId(value)) invalid("Identificativo non valido in $key.")
            if (!values.add(value)) invalid("Valore duplicato in $key: $value.")
        }
        return values
    }

    private fun JSONArray.reqObjectAt(index: Int): JSONObject {
        return opt(index) as? JSONObject ?: invalid("Elemento $index non valido.")
    }
}
