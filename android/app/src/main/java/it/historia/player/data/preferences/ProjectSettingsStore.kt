package it.historia.player.data.preferences

import android.content.Context
import it.historia.player.domain.player.DeviceCatalog
import it.historia.player.domain.player.DeviceDefinition

data class ProjectSettings(
    val culturalAssetName: String,
    val schoolName: String,
    val trackCount: Int,
    val profileLabels: Map<String, String>,
    val languageLabels: Map<String, String>,
    val customProfiles: Set<String>,
    val customLanguagesByProfile: Map<String, Set<String>>,
    val enabledSeries: Set<String>
)

class ProjectSettingsStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(device: DeviceDefinition): ProjectSettings {
        val defaults = defaultsFor(device)
        val prefix = keyPrefix(device.deviceId)
        val customProfiles = prefs.getStringSet("$prefix.custom_profiles", null)
            .orEmpty()
            .filter { it.isNotBlank() }
            .toSet()
        val profileIds = device.profiles.map { it.profileId } + customProfiles.sorted()
        val customLanguagesByProfile = profileIds.associateWith { profileId ->
            prefs.getStringSet("$prefix.profile_languages.$profileId", null)
                .orEmpty()
                .filter { it.isNotBlank() }
                .toSet()
        }.filterValues { it.isNotEmpty() }
        val languageIds = device.profiles
            .flatMap { it.languages }
            .plus(customLanguagesByProfile.values.flatten())
            .distinct()

        val profileLabels = profileIds.associate { profileId ->
            val defaultLabel = defaults.profileLabels[profileId] ?: profileId
            val saved = prefs.getString("$prefix.profile.$profileId", null)
            profileId to (saved ?: defaultLabel)
        }
        val languageLabels = languageIds.associate { language ->
            val defaultLabel = defaults.languageLabels[language] ?: language
            val saved = prefs.getString("$prefix.language.$language", null)
            language to (saved ?: defaultLabel)
        }
        val validSeriesKeys = seriesKeysFor(device, customProfiles, customLanguagesByProfile)
        val enabledSeries = prefs.getStringSet("$prefix.enabled_series", null)
            ?.filter { key -> validSeriesKeys.contains(key) }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?: (defaults.enabledSeries + customLanguagesByProfile.flatMap { (profileId, languages) ->
                languages.map { language -> seriesKey(profileId, language) }
            }
                .toSet())
                .filter { validSeriesKeys.contains(it) }
                .toSet()

        return ProjectSettings(
            culturalAssetName = prefs.getString("$prefix.name", null)
                ?: defaults.culturalAssetName,
            schoolName = prefs.getString("$prefix.school", null)
                ?: defaults.schoolName,
            trackCount = prefs.getInt("$prefix.track_count", defaults.trackCount)
                .coerceIn(MIN_TRACK_COUNT, MAX_TRACK_COUNT),
            profileLabels = profileLabels,
            languageLabels = languageLabels,
            customProfiles = customProfiles,
            customLanguagesByProfile = customLanguagesByProfile,
            enabledSeries = enabledSeries
        )
    }

    fun save(deviceId: String, settings: ProjectSettings) {
        val editor = prefs.edit()
        entriesFor(deviceId, settings).forEach { (key, value) -> editor.putTyped(key, value) }
        editor.commit()
    }

    /** Chiavi di questo store che appartengono al progetto [deviceId]. */
    fun projectSection(deviceId: String): ProjectPreferenceSection {
        val prefix = "${keyPrefix(deviceId)}."
        return ProjectPreferenceSection(PREFS_NAME, prefs) { key -> key.startsWith(prefix) }
    }

    /** Sostituisce integralmente le impostazioni del progetto, eliminando eventuali residui. */
    fun replaceProject(deviceId: String, settings: ProjectSettings): Boolean {
        return projectSection(deviceId).replaceAll(entriesFor(deviceId, settings))
    }

    private fun entriesFor(deviceId: String, settings: ProjectSettings): Map<String, Any?> {
        val prefix = keyPrefix(deviceId)
        val entries = linkedMapOf<String, Any?>(
            "$prefix.name" to settings.culturalAssetName,
            "$prefix.school" to settings.schoolName,
            "$prefix.track_count" to settings.trackCount.coerceIn(MIN_TRACK_COUNT, MAX_TRACK_COUNT),
            "$prefix.custom_profiles" to settings.customProfiles,
            "$prefix.enabled_series" to settings.enabledSeries
        )
        val profileIds = settings.profileLabels.keys + settings.customLanguagesByProfile.keys
        profileIds.forEach { profileId ->
            entries["$prefix.profile_languages.$profileId"] =
                settings.customLanguagesByProfile[profileId].orEmpty()
        }
        settings.profileLabels.forEach { (profileId, label) ->
            entries["$prefix.profile.$profileId"] = label
        }
        settings.languageLabels.forEach { (language, label) ->
            entries["$prefix.language.$language"] = label
        }
        return entries
    }

    fun reset(device: DeviceDefinition) {
        val current = load(device)
        val prefix = keyPrefix(device.deviceId)
        val profileIds = current.profileLabels.keys + current.customLanguagesByProfile.keys
        val editor = prefs.edit()
            .remove("$prefix.name")
            .remove("$prefix.school")
            .remove("$prefix.track_count")
            .remove("$prefix.custom_profiles")
            .remove("$prefix.enabled_series")

        profileIds.forEach { profileId ->
            editor
                .remove("$prefix.profile.$profileId")
                .remove("$prefix.profile_languages.$profileId")
        }
        current.languageLabels.keys.forEach { language ->
            editor.remove("$prefix.language.$language")
        }

        editor.commit()
    }

    companion object {
        private const val PREFS_NAME = "historia_project_settings_prefs"
        const val MIN_TRACK_COUNT = 1
        const val MAX_TRACK_COUNT = 99

        fun seriesKey(profileId: String, language: String): String {
            return "$profileId:$language"
        }

        /**
         * Tutte le serie (tipologia, lingua) del progetto, attive o meno, nell'ordine dell'app:
         * prima le tipologie predefinite con le loro lingue, poi quelle personalizzate.
         */
        fun projectSeries(device: DeviceDefinition, settings: ProjectSettings): List<Pair<String, String>> {
            val builtIn = device.profiles.flatMap { profile ->
                (profile.languages + settings.customLanguagesByProfile[profile.profileId].orEmpty().sorted())
                    .distinct()
                    .map { language -> profile.profileId to language }
            }
            val custom = settings.customProfiles
                .filter { profileId -> device.profiles.none { it.profileId == profileId } }
                .sorted()
                .flatMap { profileId ->
                    settings.customLanguagesByProfile[profileId].orEmpty().sorted().map { language ->
                        profileId to language
                    }
                }
            return builtIn + custom
        }

        fun defaultProfileLabel(profileId: String): String {
            return when (profileId) {
                DeviceCatalog.PROFILE_ADULT -> "Adulti"
                else -> profileId
            }
        }

        fun defaultLanguageLabel(language: String): String {
            return when (language) {
                DeviceCatalog.LANGUAGE_IT -> "Italiano"
                DeviceCatalog.LANGUAGE_EN -> "Inglese"
                DeviceCatalog.LANGUAGE_ES -> "Spagnolo"
                "fr" -> "Francese"
                "de" -> "Tedesco"
                "pt" -> "Portoghese"
                "nl" -> "Olandese"
                "ru" -> "Russo"
                "uk" -> "Ucraino"
                "pl" -> "Polacco"
                "ro" -> "Rumeno"
                "el" -> "Greco"
                "tr" -> "Turco"
                "ar" -> "Arabo"
                "zh" -> "Cinese"
                "ja" -> "Giapponese"
                "ko" -> "Coreano"
                "hi" -> "Hindi"
                "bn" -> "Bengalese"
                "ur" -> "Urdu"
                "pa" -> "Punjabi"
                "ps" -> "Pashto"
                "sd" -> "Sindhi"
                "ta" -> "Tamil"
                "te" -> "Telugu"
                "ml" -> "Malayalam"
                "gu" -> "Gujarati"
                "mr" -> "Marathi"
                "fa" -> "Persiano"
                "uz" -> "Uzbeco"
                "tk" -> "Turkmeno"
                "he" -> "Ebraico"
                "sv" -> "Svedese"
                "no" -> "Norvegese"
                "da" -> "Danese"
                "fi" -> "Finlandese"
                "cs" -> "Ceco"
                "sk" -> "Slovacco"
                "sl" -> "Sloveno"
                "hr" -> "Croato"
                "sr" -> "Serbo"
                "bg" -> "Bulgaro"
                "hu" -> "Ungherese"
                "ca" -> "Catalano"
                "eu" -> "Basco"
                "gl" -> "Galiziano"
                "ga" -> "Irlandese"
                "mt" -> "Maltese"
                "et" -> "Estone"
                "lv" -> "Lettone"
                "lt" -> "Lituano"
                "sq" -> "Albanese"
                "mk" -> "Macedone"
                "hy" -> "Armeno"
                "ka" -> "Georgiano"
                "vi" -> "Vietnamita"
                "th" -> "Thailandese"
                "id" -> "Indonesiano"
                "ms" -> "Malese"
                "sw" -> "Swahili"
                "af" -> "Afrikaans"
                else -> language.uppercase()
            }
        }

        private fun defaultsFor(device: DeviceDefinition): ProjectSettings {
            return ProjectSettings(
                culturalAssetName = device.displayName,
                schoolName = defaultSchoolName(device.credit),
                trackCount = device.profiles
                    .maxOfOrNull { it.trackCount }
                    ?.coerceIn(MIN_TRACK_COUNT, MAX_TRACK_COUNT)
                    ?: 1,
                profileLabels = device.profiles.associate { profile ->
                    profile.profileId to defaultProfileLabel(profile.profileId)
                },
                languageLabels = device.profiles
                    .flatMap { it.languages }
                    .distinct()
                    .associateWith { language -> defaultLanguageLabel(language) },
                customProfiles = emptySet(),
                customLanguagesByProfile = emptyMap(),
                enabledSeries = defaultSeriesKeys(device)
            )
        }

        private fun defaultSchoolName(credit: String): String {
            return credit
                .removePrefix("Realizzato dal ")
                .removePrefix("Realizzato dalla ")
                .removePrefix("Realizzato da ")
        }

        private fun defaultSeriesKeys(device: DeviceDefinition): Set<String> {
            return device.profiles
                .flatMap { profile ->
                    profile.languages.map { language ->
                        seriesKey(profile.profileId, language)
                    }
                }
                .toSet()
        }

        private fun seriesKeysFor(
            device: DeviceDefinition,
            customProfiles: Set<String>,
            customLanguagesByProfile: Map<String, Set<String>>
        ): Set<String> {
            val defaultKeys = device.profiles.flatMap { profile ->
                val languages = profile.languages + customLanguagesByProfile[profile.profileId].orEmpty()
                languages.map { language -> seriesKey(profile.profileId, language) }
            }
            val customKeys = customProfiles.flatMap { profileId ->
                customLanguagesByProfile[profileId].orEmpty().map { language ->
                    seriesKey(profileId, language)
                }
            }
            return (defaultKeys + customKeys).toSet()
        }

        private fun keyPrefix(deviceId: String): String {
            return "device.$deviceId"
        }
    }
}
