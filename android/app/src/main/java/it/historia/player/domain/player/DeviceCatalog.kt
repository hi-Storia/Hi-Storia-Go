package it.historia.player.domain.player

data class DeviceProfileDefinition(
    val profileId: String,
    val languages: List<String>,
    val trackCount: Int
)

data class DeviceDefinition(
    val deviceId: String,
    val displayName: String,
    val credit: String,
    val profiles: List<DeviceProfileDefinition>
)

object DeviceCatalog {
    const val DEVICE_DEFAULT_PROJECT = "default_project"
    const val PROFILE_ADULT = "adult"
    const val LANGUAGE_IT = "it"
    const val LANGUAGE_EN = "en"
    const val LANGUAGE_ES = "es"

    val devices: List<DeviceDefinition> = listOf(
        DeviceDefinition(
            deviceId = DEVICE_DEFAULT_PROJECT,
            displayName = "Il tuo bene culturale",
            credit = "La tua scuola",
            profiles = listOf(
                DeviceProfileDefinition(
                    profileId = PROFILE_ADULT,
                    languages = listOf(LANGUAGE_IT),
                    trackCount = 1
                )
            )
        )
    )

    fun findDevice(deviceId: String): DeviceDefinition? {
        return devices.firstOrNull { it.deviceId == deviceId }
    }

    fun getMaxTrack(deviceId: String, profile: String): Int {
        val device = findDevice(deviceId) ?: devices.first()
        val selectedProfile = device.profiles.firstOrNull { it.profileId == profile }
            ?: device.profiles.firstOrNull { it.profileId == PROFILE_ADULT }
            ?: device.profiles.firstOrNull()
        return selectedProfile?.trackCount?.coerceAtLeast(1) ?: 1
    }
}
