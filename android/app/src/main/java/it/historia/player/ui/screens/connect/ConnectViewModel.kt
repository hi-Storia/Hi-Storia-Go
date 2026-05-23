package it.historia.player.ui.screens.connect

import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.historia.player.data.audio.AudioEngine
import it.historia.player.data.audio.UserAudioStore
import it.historia.player.data.preferences.BackgroundMusicSettingsStore
import it.historia.player.data.preferences.ExplorationSettings
import it.historia.player.data.preferences.ExplorationSettingsStore
import it.historia.player.data.preferences.IdleSettingsStore
import it.historia.player.data.preferences.PlaybackSelectionStore
import it.historia.player.data.preferences.ProjectSettings
import it.historia.player.data.preferences.ProjectSettingsStore
import it.historia.player.data.preferences.SignalMappingStore
import it.historia.player.data.usb.UsbConnectionState
import it.historia.player.data.usb.UsbSerialManager
import it.historia.player.data.util.SerialLogBuffer
import it.historia.player.domain.model.PlaybackContext
import it.historia.player.domain.model.PlayerEvent
import it.historia.player.domain.player.DeviceCatalog
import it.historia.player.domain.player.DeviceDefinition
import it.historia.player.domain.player.DeviceProfileDefinition
import it.historia.player.domain.player.TrackResolver
import it.historia.player.domain.protocol.ProtocolParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.util.Locale

enum class ConnectionState {
    IDLE,
    MOCK_CONNECTED,
    ERROR
}

data class DeviceOptionUi(
    val deviceId: String,
    val displayName: String,
    val credit: String
)

data class SignalTrackMappingUi(
    val signalIndex: Int,
    val trackKey: String,
    val label: String = "",
    val volumePercent: Int = 100
)

data class ProjectSeriesUi(
    val profileId: String,
    val language: String,
    val enabled: Boolean,
    val canRemove: Boolean = false
)

data class ProjectSettingsUi(
    val culturalAssetName: String = "",
    val schoolName: String = "",
    val trackCount: Int = 1,
    val profileLabels: Map<String, String> = emptyMap(),
    val languageLabels: Map<String, String> = emptyMap(),
    val series: List<ProjectSeriesUi> = emptyList(),
    val removableProfiles: Set<String> = emptySet()
)

data class UserAudioTrackUi(
    val trackKey: String,
    val trackId: Int?,
    val fileName: String,
    val relativePath: String,
    val sourceLabel: String,
    val associatedSignals: List<Int> = emptyList(),
    val canRemove: Boolean = false,
    val inProjectRange: Boolean
)

data class IdleSettingsUi(
    val timeoutMinutes: Int = 5,
    val useDefaultTrack: Boolean = true,
    val customTrackKey: String? = null
)

data class BackgroundMusicSettingsUi(
    val enabled: Boolean = false,
    val useDefaultTrack: Boolean = true,
    val customTrackKey: String? = null,
    val onlyDuringTracks: Boolean = false,
    val fullVolumePercent: Int = 100,
    val duckedVolumePercent: Int = 8
)

data class ExplorationSettingsUi(
    val timeoutMinutes: Int = 3,
    val useDefaultTrack: Boolean = true,
    val customTrackKey: String? = null
)

data class ConnectUiState(
    val connectionState: ConnectionState = ConnectionState.IDLE,
    val usbConnectionState: UsbConnectionState = UsbConnectionState.DISCONNECTED,
    val userMessage: String? = "Collega il totem via USB",
    val devices: List<DeviceOptionUi> = emptyList(),
    val availableProfiles: List<String> = emptyList(),
    val availableLanguages: List<String> = emptyList(),
    val selectedContext: PlaybackContext = PlaybackContext(),
    val selectedDeviceDisplayName: String = "",
    val selectedDeviceCredit: String = "",
    val contextLabel: String = "",
    val maxTrackForSelectedProfile: Int = 1,
    val currentThresholdUi: Int = 6500,
    val nowPlaying: Int? = null,
    val nowPlayingSignal: Int? = null,
    val signalTrackMappings: List<SignalTrackMappingUi> = emptyList(),
    val projectSettings: ProjectSettingsUi = ProjectSettingsUi(),
    val userAudioTracks: List<UserAudioTrackUi> = emptyList(),
    val availableAudioTracks: List<UserAudioTrackUi> = emptyList(),
    val idleSettings: IdleSettingsUi = IdleSettingsUi(),
    val backgroundMusicSettings: BackgroundMusicSettingsUi = BackgroundMusicSettingsUi(),
    val explorationSettings: ExplorationSettingsUi = ExplorationSettingsUi(),
    val isExplorationMode: Boolean = false,
    val isIdlePlaying: Boolean = false,
    val projectMessage: String? = null,
    val serialLogs: List<String> = emptyList()
)

class ConnectViewModel(
    private val usbSerialManager: UsbSerialManager,
    private val audioEngine: AudioEngine,
    private val userAudioStore: UserAudioStore,
    private val backgroundMusicSettingsStore: BackgroundMusicSettingsStore,
    private val explorationSettingsStore: ExplorationSettingsStore,
    private val idleSettingsStore: IdleSettingsStore,
    private val playbackSelectionStore: PlaybackSelectionStore,
    private val projectSettingsStore: ProjectSettingsStore,
    private val signalMappingStore: SignalMappingStore,
    private val serialLogBuffer: SerialLogBuffer,
    initialPlaybackContext: PlaybackContext = PlaybackContext(
        deviceId = DeviceCatalog.DEVICE_DEFAULT_PROJECT,
        profile = DeviceCatalog.PROFILE_ADULT,
        language = DeviceCatalog.LANGUAGE_IT
    )
) : ViewModel() {
    private var idleJob: Job? = null
    private var explorationJob: Job? = null
    private var activeBackgroundPath: String? = null
    private val lastExplorationSignalAt = mutableMapOf<Int, Long>()

    private val deviceOptions: List<DeviceOptionUi> = DeviceCatalog.devices.map {
        DeviceOptionUi(
            deviceId = it.deviceId,
            displayName = it.displayName,
            credit = it.credit
        )
    }

    private val _uiState = MutableStateFlow(
        createInitialState(playbackSelectionStore.loadOrNull() ?: initialPlaybackContext)
    )
    val uiState: StateFlow<ConnectUiState> = _uiState.asStateFlow()

    init {
        persistSelectedContext(uiState.value.selectedContext)
        usbSerialManager.setMockMaxTrack(uiState.value.maxTrackForSelectedProfile)
        audioEngine.setOnPlaybackEnded(::onPlaybackEnded)
        applyBackgroundMusic(uiState.value)
        scheduleIdlePlayback(uiState.value.idleSettings.timeoutMinutes)

        viewModelScope.launch {
            serialLogBuffer.linesFlow.collect { lines ->
                _uiState.update { it.copy(serialLogs = lines) }
            }
        }

        viewModelScope.launch {
            usbSerialManager.connectionStatus.collect { status ->
                _uiState.update { current ->
                    current.copy(
                        usbConnectionState = status.state,
                        userMessage = when (status.state) {
                            UsbConnectionState.DISCONNECTED -> "Collega il totem via USB"
                            UsbConnectionState.WAITING_PERMISSION -> "Autorizza il collegamento USB"
                            UsbConnectionState.CONNECTED -> null
                        }
                    )
                }
            }
        }

        viewModelScope.launch {
            usbSerialManager.incomingLines
                .catch { throwable ->
                    appendLog("ERR: incomingLines ${throwable.message ?: "unknown"}")
                    _uiState.update { it.copy(connectionState = ConnectionState.ERROR) }
                }
                .collect { line ->
                    val event = ProtocolParser.parse(line)
                    if (event is PlayerEvent.Message) {
                        tryUpdateThresholdFromMessage(event.text)
                    }
                    onPlayerEvent(event)
                }
        }

        runCatching {
            usbSerialManager.startUsbAutoconnect()
        }.onFailure { throwable ->
            appendLog("ERR: startUsbAutoconnect ${throwable.message ?: "unknown"}")
        }
    }

    fun startMock() {
        runCatching {
            usbSerialManager.stopUsb()
            usbSerialManager.startMock(uiState.value.maxTrackForSelectedProfile)
            _uiState.update { it.copy(connectionState = ConnectionState.MOCK_CONNECTED) }
            appendLog("USB: mock mode started")
        }.onFailure { throwable ->
            appendLog("ERR: startMock ${throwable.message ?: "unknown"}")
            _uiState.update { it.copy(connectionState = ConnectionState.ERROR) }
        }
    }

    fun stopPlayback() {
        runCatching {
            if (uiState.value.isExplorationMode) {
                stopExplorationMode()
                return
            }
            audioEngine.stop()
            _uiState.update { it.copy(nowPlaying = null, nowPlayingSignal = null, isIdlePlaying = false) }
            applyBackgroundMusic(uiState.value)
            scheduleIdlePlayback(uiState.value.idleSettings.timeoutMinutes)
        }.onFailure { throwable ->
            appendLog("ERR: stopPlayback ${throwable.message ?: "unknown"}")
        }
    }

    fun startExplorationMode() {
        idleJob?.cancel()
        explorationJob?.cancel()
        lastExplorationSignalAt.clear()
        runCatching {
            audioEngine.stop()
            audioEngine.stopBackground()
            activeBackgroundPath = null
            _uiState.update {
                it.copy(
                    nowPlaying = null,
                    nowPlayingSignal = null,
                    isIdlePlaying = false,
                    isExplorationMode = true,
                    userMessage = "Modalita esplorazione attiva"
                )
            }
            val introPath = resolveExplorationIntroPath(uiState.value)
            if (introPath == null) {
                showProjectMessage("Carica una traccia esplorazione o riattiva quella default.")
            } else {
                audioEngine.playAsset(introPath)
                appendLog("PLAY: exploration intro -> $introPath")
            }
            explorationJob = viewModelScope.launch {
                delay(uiState.value.explorationSettings.timeoutMinutes.coerceAtLeast(1) * 60_000L)
                stopExplorationMode()
            }
        }.onFailure { throwable ->
            appendLog("ERR: start exploration ${throwable.message ?: "unknown"}")
        }
    }

    fun stopExplorationMode() {
        explorationJob?.cancel()
        explorationJob = null
        lastExplorationSignalAt.clear()
        runCatching {
            audioEngine.stop()
            _uiState.update {
                it.copy(
                    nowPlaying = null,
                    nowPlayingSignal = null,
                    isIdlePlaying = false,
                    isExplorationMode = false,
                    userMessage = null
                )
            }
            resetArduinoActiveTrack("exploration ended")
            applyBackgroundMusic(uiState.value)
            scheduleIdlePlayback(uiState.value.idleSettings.timeoutMinutes)
            appendLog("MODE: exploration stopped")
        }.onFailure { throwable ->
            appendLog("ERR: stop exploration ${throwable.message ?: "unknown"}")
        }
    }

    fun onDeviceSelected(deviceId: String) {
        updateContextAndPersist(resetNowPlaying = true) { current ->
            current.selectedContext.copy(deviceId = deviceId)
        }
    }

    fun onProfileSelected(profile: String) {
        updateContextAndPersist(resetNowPlaying = true) { current ->
            current.selectedContext.copy(profile = profile)
        }
    }

    fun onLanguageSelected(language: String) {
        updateContextAndPersist(resetNowPlaying = false) { current ->
            current.selectedContext.copy(language = language)
        }
    }

    fun onProjectNameChanged(name: String) {
        updateProjectSettings { it.copy(culturalAssetName = name) }
    }

    fun onProjectSchoolChanged(schoolName: String) {
        updateProjectSettings { it.copy(schoolName = schoolName) }
    }

    fun onProjectTrackCountChanged(value: String) {
        val parsed = value.toIntOrNull() ?: return
        updateProjectSettings {
            it.copy(
                trackCount = parsed.coerceIn(
                    ProjectSettingsStore.MIN_TRACK_COUNT,
                    ProjectSettingsStore.MAX_TRACK_COUNT
                )
            )
        }
    }

    fun onIdleTimeoutChanged(value: String) {
        val parsed = value.toIntOrNull() ?: return
        val timeout = parsed.coerceIn(
            IdleSettingsStore.MIN_TIMEOUT_MINUTES,
            IdleSettingsStore.MAX_TIMEOUT_MINUTES
        )
        val deviceId = uiState.value.selectedContext.deviceId
        val current = idleSettingsStore.load(deviceId)
        idleSettingsStore.save(deviceId, current.copy(timeoutMinutes = timeout))
        _uiState.update { it.copy(idleSettings = it.idleSettings.copy(timeoutMinutes = timeout)) }
        scheduleIdlePlayback(timeout)
    }

    fun onExplorationTimeoutChanged(value: String) {
        val parsed = value.toIntOrNull() ?: return
        val timeout = parsed.coerceIn(
            ExplorationSettingsStore.MIN_TIMEOUT_MINUTES,
            ExplorationSettingsStore.MAX_TIMEOUT_MINUTES
        )
        val deviceId = uiState.value.selectedContext.deviceId
        explorationSettingsStore.save(deviceId, ExplorationSettings(timeout))
        _uiState.update { it.copy(explorationSettings = ExplorationSettingsUi(timeoutMinutes = timeout)) }
        if (uiState.value.isExplorationMode) {
            explorationJob?.cancel()
            explorationJob = viewModelScope.launch {
                delay(timeout.coerceAtLeast(1) * 60_000L)
                stopExplorationMode()
            }
        }
    }

    fun onIdleUseDefaultTrackChanged(useDefault: Boolean) {
        val context = uiState.value.selectedContext
        val current = idleSettingsStore.loadTrackSettings(context)
        idleSettingsStore.saveTrackSettings(context, current.copy(useDefaultTrack = useDefault))
        _uiState.update { it.copy(idleSettings = it.idleSettings.copy(useDefaultTrack = useDefault)) }
        scheduleIdlePlayback(uiState.value.idleSettings.timeoutMinutes)
    }

    fun onIdleAudioFileSelected(uri: Uri?) {
        if (uri == null) return
        val state = uiState.value
        val imported = userAudioStore.importIdleTrack(
            context = state.selectedContext,
            sourceUri = uri
        )
        if (imported == null) {
            showProjectMessage("Non sono riuscito a importare la traccia idle.")
            return
        }

        val trackSettings = idleSettingsStore.loadTrackSettings(state.selectedContext)
        idleSettingsStore.saveTrackSettings(
            state.selectedContext,
            trackSettings.copy(useDefaultTrack = false, customTrackKey = imported.trackKey)
        )
        _uiState.update { current ->
            applyContextSelection(current, current.selectedContext).copy(
                projectMessage = "Traccia idle caricata: ${imported.fileName}."
            )
        }
        scheduleIdlePlayback(uiState.value.idleSettings.timeoutMinutes)
    }

    fun onExplorationUseDefaultTrackChanged(useDefault: Boolean) {
        val context = uiState.value.selectedContext
        val current = explorationSettingsStore.loadTrackSettings(context)
        explorationSettingsStore.saveTrackSettings(context, current.copy(useDefaultTrack = useDefault))
        _uiState.update {
            it.copy(explorationSettings = it.explorationSettings.copy(useDefaultTrack = useDefault))
        }
    }

    fun onExplorationAudioFileSelected(uri: Uri?) {
        if (uri == null) return
        val state = uiState.value
        val imported = userAudioStore.importExplorationTrack(
            context = state.selectedContext,
            sourceUri = uri
        )
        if (imported == null) {
            showProjectMessage("Non sono riuscito a importare la traccia esplorazione.")
            return
        }

        val trackSettings = explorationSettingsStore.loadTrackSettings(state.selectedContext)
        explorationSettingsStore.saveTrackSettings(
            state.selectedContext,
            trackSettings.copy(useDefaultTrack = false, customTrackKey = imported.trackKey)
        )
        _uiState.update { current ->
            applyContextSelection(current, current.selectedContext).copy(
                projectMessage = "Traccia esplorazione caricata: ${imported.fileName}."
            )
        }
    }

    fun onBackgroundMusicEnabledChanged(enabled: Boolean) {
        val deviceId = uiState.value.selectedContext.deviceId
        val updated = backgroundMusicSettingsStore.load(deviceId).copy(enabled = enabled)
        backgroundMusicSettingsStore.save(deviceId, updated)
        _uiState.update {
            it.copy(
                backgroundMusicSettings = it.backgroundMusicSettings.copy(enabled = enabled),
                projectMessage = null
            )
        }
        applyBackgroundMusic(uiState.value)
    }

    fun onBackgroundMusicUseDefaultChanged(useDefault: Boolean) {
        val deviceId = uiState.value.selectedContext.deviceId
        val updated = backgroundMusicSettingsStore.load(deviceId).copy(useDefaultTrack = useDefault)
        backgroundMusicSettingsStore.save(deviceId, updated)
        _uiState.update {
            it.copy(
                backgroundMusicSettings = it.backgroundMusicSettings.copy(useDefaultTrack = useDefault),
                projectMessage = null
            )
        }
        applyBackgroundMusic(uiState.value)
    }

    fun onBackgroundMusicOnlyDuringTracksChanged(onlyDuringTracks: Boolean) {
        val deviceId = uiState.value.selectedContext.deviceId
        val updated = backgroundMusicSettingsStore.load(deviceId).copy(onlyDuringTracks = onlyDuringTracks)
        backgroundMusicSettingsStore.save(deviceId, updated)
        _uiState.update {
            it.copy(
                backgroundMusicSettings = it.backgroundMusicSettings.copy(
                    onlyDuringTracks = onlyDuringTracks
                ),
                projectMessage = null
            )
        }
        applyBackgroundMusic(uiState.value)
    }

    fun onBackgroundMusicFileSelected(uri: Uri?) {
        if (uri == null) return
        val deviceId = uiState.value.selectedContext.deviceId
        val imported = userAudioStore.importBackgroundTrack(
            deviceId = deviceId,
            sourceUri = uri
        )
        if (imported == null) {
            showProjectMessage("Non sono riuscito a importare la musica di background.")
            return
        }

        val updated = backgroundMusicSettingsStore.load(deviceId).copy(
            enabled = true,
            useDefaultTrack = false,
            customTrackKey = imported.trackKey
        )
        backgroundMusicSettingsStore.save(deviceId, updated)
        _uiState.update { current ->
            applyContextSelection(current, current.selectedContext).copy(
                projectMessage = "Musica caricata: ${imported.fileName}."
            )
        }
        applyBackgroundMusic(uiState.value)
    }

    fun onBackgroundMusicFullVolumeChanged(percent: Int) {
        val deviceId = uiState.value.selectedContext.deviceId
        val current = backgroundMusicSettingsStore.load(deviceId)
        val fullVolume = percent.coerceIn(0, 100)
        val updated = current.copy(
            fullVolumePercent = fullVolume,
            duckedVolumePercent = current.duckedVolumePercent.coerceAtMost(fullVolume)
        )
        backgroundMusicSettingsStore.save(deviceId, updated)
        _uiState.update {
            it.copy(
                backgroundMusicSettings = it.backgroundMusicSettings.copy(
                    fullVolumePercent = updated.fullVolumePercent,
                    duckedVolumePercent = updated.duckedVolumePercent
                )
            )
        }
        applyBackgroundMusic(uiState.value)
    }

    fun onBackgroundMusicDuckedVolumeChanged(percent: Int) {
        val deviceId = uiState.value.selectedContext.deviceId
        val current = backgroundMusicSettingsStore.load(deviceId)
        val updated = current.copy(
            duckedVolumePercent = percent.coerceIn(0, current.fullVolumePercent.coerceIn(0, 100))
        )
        backgroundMusicSettingsStore.save(deviceId, updated)
        _uiState.update {
            it.copy(
                backgroundMusicSettings = it.backgroundMusicSettings.copy(
                    duckedVolumePercent = updated.duckedVolumePercent
                )
            )
        }
        applyBackgroundMusic(uiState.value)
    }

    fun onProjectProfileLabelChanged(profileId: String, label: String) {
        updateProjectSettings { settings ->
            settings.copy(
                profileLabels = settings.profileLabels + (profileId to label)
            )
        }
    }

    fun onProjectLanguageLabelChanged(language: String, label: String) {
        updateProjectSettings { settings ->
            settings.copy(
                languageLabels = settings.languageLabels + (language to label)
            )
        }
    }

    fun onProjectSeriesEnabledChanged(profileId: String, language: String, enabled: Boolean) {
        val key = ProjectSettingsStore.seriesKey(profileId, language)
        val currentSettings = loadProjectSettings(findDeviceOrDefault(uiState.value.selectedContext.deviceId))
        if (!enabled && currentSettings.enabledSeries.contains(key) && currentSettings.enabledSeries.size <= 1) {
            showProjectMessage("Deve restare attiva almeno una serie.")
            return
        }

        updateProjectSettings { settings ->
            val nextEnabled = if (enabled) {
                settings.enabledSeries + key
            } else {
                settings.enabledSeries - key
            }
            if (nextEnabled.isEmpty()) settings else settings.copy(enabledSeries = nextEnabled)
        }
    }

    fun onProjectProfileEnabledChanged(profileId: String, enabled: Boolean) {
        val device = findDeviceOrDefault(uiState.value.selectedContext.deviceId)
        val currentSettings = loadProjectSettings(device)
        val profileSeriesKeys = seriesKeysForProfile(profileId, device, currentSettings)
        if (profileSeriesKeys.isEmpty()) return

        if (!enabled && (currentSettings.enabledSeries - profileSeriesKeys).isEmpty()) {
            showProjectMessage("Deve restare attiva almeno una serie.")
            return
        }

        updateProjectSettings { settings ->
            val keys = seriesKeysForProfile(profileId, device, settings)
            val nextEnabled = if (enabled) {
                settings.enabledSeries + keys
            } else {
                settings.enabledSeries - keys
            }
            settings.copy(enabledSeries = nextEnabled)
        }
    }

    fun onProjectLanguageAdded(profileId: String, languageLabel: String) {
        val sanitizedLabel = languageLabel.trim()
        if (sanitizedLabel.isBlank()) return

        val device = findDeviceOrDefault(uiState.value.selectedContext.deviceId)
        val currentSettings = loadProjectSettings(device)
        val languageInput = resolveLanguageInput(sanitizedLabel, currentSettings)
        val existingLanguages = buildProjectProfiles(device, currentSettings)
            .firstOrNull { it.profileId == profileId }
            ?.languages
            .orEmpty()
        if (existingLanguages.contains(languageInput.languageId)) {
            showProjectMessage("${languageInput.displayLabel} è già presente in questa tipologia.")
            return
        }

        val matchingCustomLanguage = currentSettings.customLanguagesByProfile[profileId]
            .orEmpty()
            .firstOrNull { existingLanguage ->
                existingLanguage.startsWith("custom_lang") &&
                    compactLanguageKey(currentSettings.languageLabels[existingLanguage].orEmpty()) ==
                    compactLanguageKey(sanitizedLabel)
            }
        if (matchingCustomLanguage != null && !languageInput.languageId.startsWith("custom_lang")) {
            updateProjectSettings { settings ->
                val languagesForProfile = settings.customLanguagesByProfile[profileId]
                    .orEmpty()
                    .minus(matchingCustomLanguage)
                    .plus(languageInput.languageId)
                val wasEnabled = settings.enabledSeries.contains(
                    ProjectSettingsStore.seriesKey(profileId, matchingCustomLanguage)
                )
                val enabledWithoutCustom = settings.enabledSeries - ProjectSettingsStore.seriesKey(
                    profileId,
                    matchingCustomLanguage
                )
                val nextEnabled = if (wasEnabled) {
                    enabledWithoutCustom + ProjectSettingsStore.seriesKey(profileId, languageInput.languageId)
                } else {
                    enabledWithoutCustom
                }

                settings.copy(
                    languageLabels = (settings.languageLabels - matchingCustomLanguage) +
                        (languageInput.languageId to languageInput.displayLabel),
                    customLanguagesByProfile = settings.customLanguagesByProfile +
                        (profileId to languagesForProfile),
                    enabledSeries = nextEnabled
                )
            }
            return
        }

        updateProjectSettings { settings ->
            val updatedCustomLanguages = settings.customLanguagesByProfile.toMutableMap()
            updatedCustomLanguages[profileId] =
                updatedCustomLanguages[profileId].orEmpty() + languageInput.languageId

            settings.copy(
                languageLabels = settings.languageLabels + (languageInput.languageId to languageInput.displayLabel),
                customLanguagesByProfile = updatedCustomLanguages,
                enabledSeries = settings.enabledSeries + ProjectSettingsStore.seriesKey(
                    profileId,
                    languageInput.languageId
                )
            )
        }
    }

    fun onProjectProfileAdded(profileLabel: String, languageLabel: String) {
        val sanitizedProfileLabel = profileLabel.trim()
        val sanitizedLanguageLabel = languageLabel.trim()
        if (sanitizedProfileLabel.isBlank() || sanitizedLanguageLabel.isBlank()) return

        updateProjectSettings { settings ->
            val profileId = nextCustomId(
                prefix = "custom_profile",
                existingIds = settings.profileLabels.keys
            )
            val languageInput = resolveLanguageInput(sanitizedLanguageLabel, settings)

            settings.copy(
                profileLabels = settings.profileLabels + (profileId to sanitizedProfileLabel),
                languageLabels = settings.languageLabels + (languageInput.languageId to languageInput.displayLabel),
                customProfiles = settings.customProfiles + profileId,
                customLanguagesByProfile = settings.customLanguagesByProfile + (
                    profileId to setOf(languageInput.languageId)
                    ),
                enabledSeries = settings.enabledSeries + ProjectSettingsStore.seriesKey(
                    profileId,
                    languageInput.languageId
                )
            )
        }
    }

    fun onProjectProfileRemoved(profileId: String) {
        val device = findDeviceOrDefault(uiState.value.selectedContext.deviceId)
        val currentSettings = loadProjectSettings(device)
        if (!currentSettings.customProfiles.contains(profileId)) {
            showProjectMessage("Le tipologie predefinite si possono disattivare, non rimuovere.")
            return
        }

        val profileSeriesKeys = seriesKeysForProfile(profileId, device, currentSettings)
        if ((currentSettings.enabledSeries - profileSeriesKeys).isEmpty()) {
            showProjectMessage("Deve restare attiva almeno una serie.")
            return
        }

        updateProjectSettings { settings ->
            val removedLanguages = settings.customLanguagesByProfile[profileId].orEmpty()
            val updatedCustomLanguages = settings.customLanguagesByProfile - profileId
            val remainingLanguages = remainingLanguageIds(device, updatedCustomLanguages)

            settings.copy(
                profileLabels = settings.profileLabels - profileId,
                languageLabels = settings.languageLabels.filterKeys { language ->
                    !removedLanguages.contains(language) || remainingLanguages.contains(language)
                },
                customProfiles = settings.customProfiles - profileId,
                customLanguagesByProfile = updatedCustomLanguages,
                enabledSeries = settings.enabledSeries - profileSeriesKeys
            )
        }
    }

    fun onProjectLanguageRemoved(profileId: String, language: String) {
        val device = findDeviceOrDefault(uiState.value.selectedContext.deviceId)
        val currentSettings = loadProjectSettings(device)
        val removableLanguages = currentSettings.customLanguagesByProfile[profileId].orEmpty()
        if (!removableLanguages.contains(language)) {
            showProjectMessage("Le lingue predefinite si possono disattivare, non rimuovere.")
            return
        }

        val profileLanguages = buildProjectProfiles(device, currentSettings)
            .firstOrNull { it.profileId == profileId }
            ?.languages
            .orEmpty()
        if (profileLanguages.size <= 1) {
            showProjectMessage("Per rimuovere l'ultima lingua, rimuovi la tipologia.")
            return
        }

        val key = ProjectSettingsStore.seriesKey(profileId, language)
        if ((currentSettings.enabledSeries - key).isEmpty()) {
            showProjectMessage("Deve restare attiva almeno una serie.")
            return
        }

        updateProjectSettings { settings ->
            val updatedLanguagesForProfile = settings.customLanguagesByProfile[profileId]
                .orEmpty() - language
            val updatedCustomLanguages = if (updatedLanguagesForProfile.isEmpty()) {
                settings.customLanguagesByProfile - profileId
            } else {
                settings.customLanguagesByProfile + (profileId to updatedLanguagesForProfile)
            }
            val remainingLanguages = remainingLanguageIds(device, updatedCustomLanguages)

            settings.copy(
                languageLabels = if (remainingLanguages.contains(language)) {
                    settings.languageLabels
                } else {
                    settings.languageLabels - language
                },
                customLanguagesByProfile = updatedCustomLanguages,
                enabledSeries = settings.enabledSeries - key
            )
        }
    }

    fun onProjectMessageDismissed() {
        _uiState.update { it.copy(projectMessage = null) }
    }

    fun onProjectReset() {
        var updatedState: ConnectUiState? = null
        _uiState.update { current ->
            val device = findDeviceOrDefault(current.selectedContext.deviceId)
            projectSettingsStore.reset(device)
            val reapplied = applyContextSelection(current, current.selectedContext)
                .copy(nowPlaying = null, nowPlayingSignal = null)
            updatedState = reapplied
            reapplied
        }

        updatedState?.let { state ->
            audioEngine.stop()
            usbSerialManager.setMockMaxTrack(state.maxTrackForSelectedProfile)
            persistSelectedContext(state.selectedContext)
            applyBackgroundMusic(state)
        }
    }

    fun onThresholdChanged(value: Int) {
        val clamped = value.coerceIn(THRESHOLD_MIN, THRESHOLD_MAX)
        _uiState.update { it.copy(currentThresholdUi = clamped) }
    }

    fun sendSetThreshold() {
        val value = uiState.value.currentThresholdUi.coerceIn(THRESHOLD_MIN, THRESHOLD_MAX)
        runCatching {
            usbSerialManager.sendLine("SET:$value")
            appendLog("USB: TX SET:$value")
        }.onFailure { throwable ->
            appendLog("ERR: send SET failed ${throwable.message ?: "unknown"}")
        }
    }

    fun playSignalFromAdmin(signalIndex: Int) {
        playSignal(signalIndex)
    }

    fun onSignalTrackSelected(signalIndex: Int, trackKey: String) {
        var updatedState: ConnectUiState? = null
        _uiState.update { current ->
            val maxSignal = current.maxTrackForSelectedProfile
            if (signalIndex !in 1..maxSignal || trackKey.isBlank()) {
                return@update current
            }

            val updatedMappings = current.signalTrackMappings.map { mapping ->
                if (mapping.signalIndex == signalIndex) {
                    mapping.copy(trackKey = trackKey)
                } else {
                    mapping
                }
            }
            val shouldStop = current.nowPlayingSignal == signalIndex
            val next = current.copy(
                signalTrackMappings = updatedMappings,
                nowPlaying = if (shouldStop) null else current.nowPlaying,
                nowPlayingSignal = if (shouldStop) null else current.nowPlayingSignal
            )
            updatedState = next
            next
        }

        updatedState?.let { state ->
            signalMappingStore.save(
                context = state.selectedContext,
                mappings = state.signalTrackMappings.associate { it.signalIndex to it.trackKey },
                maxSignal = state.maxTrackForSelectedProfile
            )
            if (state.nowPlaying == null) {
                audioEngine.stop()
                applyBackgroundMusic(state)
            }
        }
    }

    fun onSignalLabelSaved(signalIndex: Int, label: String) {
        var updatedState: ConnectUiState? = null
        _uiState.update { current ->
            val maxTrack = current.maxTrackForSelectedProfile
            if (signalIndex !in 1..maxTrack) {
                return@update current
            }

            val sanitizedLabel = label.trim()
            val updatedMappings = current.signalTrackMappings.map { mapping ->
                if (mapping.signalIndex == signalIndex) {
                    mapping.copy(label = sanitizedLabel)
                } else {
                    mapping
                }
            }
            val next = current.copy(signalTrackMappings = updatedMappings)
            updatedState = next
            next
        }

        updatedState?.let { state ->
            signalMappingStore.saveLabel(
                context = state.selectedContext,
                signalIndex = signalIndex,
                label = label,
                maxTrack = state.maxTrackForSelectedProfile
            )
        }
    }

    fun onSignalVolumeChanged(signalIndex: Int, volumePercent: Int) {
        var updatedState: ConnectUiState? = null
        _uiState.update { current ->
            val maxTrack = current.maxTrackForSelectedProfile
            if (signalIndex !in 1..maxTrack) {
                return@update current
            }

            val sanitizedVolume = volumePercent.coerceIn(MIN_TRACK_VOLUME_PERCENT, MAX_TRACK_VOLUME_PERCENT)
            val updatedMappings = current.signalTrackMappings.map { mapping ->
                if (mapping.signalIndex == signalIndex) {
                    mapping.copy(volumePercent = sanitizedVolume)
                } else {
                    mapping
                }
            }
            val next = current.copy(signalTrackMappings = updatedMappings)
            updatedState = next
            next
        }

        updatedState?.let { state ->
            signalMappingStore.saveVolume(
                context = state.selectedContext,
                signalIndex = signalIndex,
                volumePercent = volumePercent,
                maxTrack = state.maxTrackForSelectedProfile
            )
            if (state.nowPlayingSignal == signalIndex) {
                audioEngine.setMainVolume(volumePercent.coerceIn(
                    MIN_TRACK_VOLUME_PERCENT,
                    MAX_TRACK_VOLUME_PERCENT
                ) / 100f)
            }
        }
    }

    fun onAudioFileSelected(signalIndex: Int, uri: Uri?) {
        if (uri == null) return
        val state = uiState.value
        if (signalIndex !in 1..state.maxTrackForSelectedProfile) {
            showProjectMessage("Il segnale selezionato non rientra nel progetto corrente.")
            return
        }
        val imported = userAudioStore.importTrack(
            context = state.selectedContext,
            sourceUri = uri
        )
        if (imported == null) {
            showProjectMessage("Non sono riuscito a importare il file audio.")
            return
        }

        onSignalTrackSelected(signalIndex, imported.trackKey)
        audioEngine.stop()
        _uiState.update { current ->
            val refreshed = applyContextSelection(current, current.selectedContext)
            refreshed.copy(
                nowPlaying = null,
                nowPlayingSignal = null,
                projectMessage = "Caricato ${imported.fileName} e associato a T$signalIndex."
            )
        }
        applyBackgroundMusic(uiState.value)
    }

    fun onAudioTrackRemoved(trackKey: String) {
        val state = uiState.value
        if (trackKey.isBlank()) return

        val track = state.userAudioTracks.firstOrNull { it.trackKey == trackKey }
        val removed = userAudioStore.deleteTrack(state.selectedContext, trackKey)
        val hiddenDefault = !removed && track?.sourceLabel == "default"
        if (hiddenDefault) {
            userAudioStore.hideAssetTrack(state.selectedContext, trackKey)
        }
        if (!removed && !hiddenDefault) {
            showProjectMessage("Non posso rimuovere questo file audio.")
            return
        }

        val fallbackTrack = state.availableAudioTracks
            .firstOrNull { it.trackKey != trackKey }
            ?.trackKey
        val updatedMappings = state.signalTrackMappings.map { mapping ->
            if (mapping.trackKey == trackKey) {
                mapping.copy(trackKey = fallbackTrack ?: "%02d.mp3".format(mapping.signalIndex))
            } else {
                mapping
            }
        }
        signalMappingStore.save(
            context = state.selectedContext,
            mappings = updatedMappings.associate { it.signalIndex to it.trackKey },
            maxSignal = state.maxTrackForSelectedProfile
        )

        audioEngine.stop()
        _uiState.update { current ->
            applyContextSelection(
                current.copy(nowPlaying = null, nowPlayingSignal = null),
                current.selectedContext
            ).copy(
                projectMessage = if (hiddenDefault) {
                    "File audio default rimosso dal progetto."
                } else {
                    "File audio rimosso."
                }
            )
        }
        applyBackgroundMusic(uiState.value)
    }

    fun onUnusedAudioTracksCleaned() {
        val state = uiState.value
        val usedTrackKeys = state.signalTrackMappings.map { it.trackKey }.toSet()
        val removedCount = userAudioStore.deleteUnusedTracks(state.selectedContext, usedTrackKeys)

        _uiState.update { current ->
            applyContextSelection(current, current.selectedContext).copy(
                projectMessage = if (removedCount == 0) {
                    "Non ci sono file caricati non associati da rimuovere."
                } else {
                    "Rimossi $removedCount file audio non associati."
                }
            )
        }
    }

    override fun onCleared() {
        idleJob?.cancel()
        explorationJob?.cancel()
        audioEngine.setOnPlaybackEnded(null)
        audioEngine.release()
        usbSerialManager.close()
        super.onCleared()
    }

    private fun onPlaybackEnded() {
        if (uiState.value.isExplorationMode) {
            _uiState.update {
                it.copy(nowPlaying = null, nowPlayingSignal = null, isIdlePlaying = false)
            }
            return
        }

        val endedSignal = uiState.value.nowPlayingSignal
        if (endedSignal != null) {
            resetArduinoActiveTrack("ended T$endedSignal")
        }

        _uiState.update {
            it.copy(nowPlaying = null, nowPlayingSignal = null, isIdlePlaying = false)
        }
        applyBackgroundMusic(uiState.value)
        scheduleIdlePlayback(uiState.value.idleSettings.timeoutMinutes)
    }

    private fun resetArduinoActiveTrack(reason: String) {
        runCatching {
            usbSerialManager.sendLine("Z")
            appendLog("USB: TX Z ($reason)")
        }.onFailure { throwable ->
            appendLog("ERR: send Z failed ${throwable.message ?: "unknown"}")
        }
    }

    private fun applyBackgroundMusic(state: ConnectUiState) {
        val foregroundPlaying = state.nowPlayingSignal != null || state.isIdlePlaying
        audioEngine.setBackgroundVolumeLevels(
            fullVolume = state.backgroundMusicSettings.fullVolumePercent / 100f,
            duckedVolume = state.backgroundMusicSettings.duckedVolumePercent / 100f
        )
        val backgroundPath = resolveBackgroundMusicPath(state)
        if (backgroundPath == null ||
            (state.backgroundMusicSettings.onlyDuringTracks && !foregroundPlaying)
        ) {
            audioEngine.stopBackground()
            activeBackgroundPath = null
            return
        }

        if (activeBackgroundPath != backgroundPath) {
            audioEngine.setBackgroundDucked(foregroundPlaying)
            audioEngine.playBackgroundAsset(backgroundPath)
            activeBackgroundPath = backgroundPath
        } else {
            audioEngine.setBackgroundDucked(foregroundPlaying)
        }
    }

    private fun resolveBackgroundMusicPath(state: ConnectUiState): String? {
        val settings = state.backgroundMusicSettings
        if (!settings.enabled) {
            return null
        }

        return if (settings.useDefaultTrack) {
            DEFAULT_BACKGROUND_ASSET_PATH
        } else {
            settings.customTrackKey
                ?.let { userAudioStore.backgroundRelativePathFor(state.selectedContext.deviceId, it) }
        }
    }

    private fun resolveExplorationIntroPath(state: ConnectUiState): String? {
        val settings = state.explorationSettings
        return if (settings.useDefaultTrack) {
            DEFAULT_EXPLORATION_INTRO_ASSET_PATH
        } else {
            settings.customTrackKey
                ?.let { userAudioStore.explorationRelativePathFor(state.selectedContext, it) }
        }
    }

    private fun scheduleIdlePlayback(timeoutMinutes: Int) {
        idleJob?.cancel()
        if (uiState.value.isExplorationMode) {
            return
        }
        val timeoutMillis = timeoutMinutes.coerceAtLeast(1) * 60_000L
        idleJob = viewModelScope.launch {
            delay(timeoutMillis)
            playIdleTrack()
        }
    }

    private fun playIdleTrack() {
        val state = uiState.value
        if (state.nowPlayingSignal != null || state.isIdlePlaying) {
            return
        }
        val idlePath = if (state.idleSettings.useDefaultTrack) {
            DEFAULT_IDLE_ASSET_PATH
        } else {
            state.idleSettings.customTrackKey
                ?.let { userAudioStore.idleRelativePathFor(state.selectedContext, it) }
                ?: return
        }

        runCatching {
            audioEngine.setMainVolume(1f)
            audioEngine.playAsset(idlePath)
            _uiState.update {
                it.copy(
                    nowPlaying = null,
                    nowPlayingSignal = null,
                    isIdlePlaying = true
                )
            }
            applyBackgroundMusic(uiState.value)
            appendLog("PLAY: idle -> $idlePath")
        }.onFailure { throwable ->
            appendLog("ERR: play idle ${throwable.message ?: "unknown"}")
        }
    }

    private fun updateContextAndPersist(
        resetNowPlaying: Boolean,
        contextBuilder: (ConnectUiState) -> PlaybackContext
    ) {
        var updatedState: ConnectUiState? = null
        _uiState.update { current ->
            val requested = contextBuilder(current)
            val resolved = applyContextSelection(current, requested)
            val finalState = if (resetNowPlaying) {
                resolved.copy(nowPlaying = null, nowPlayingSignal = null)
            } else {
                resolved
            }
            updatedState = finalState
            finalState
        }

        updatedState?.let { state ->
            usbSerialManager.setMockMaxTrack(state.maxTrackForSelectedProfile)
            persistSelectedContext(state.selectedContext)
            applyBackgroundMusic(state)
        }
    }

    private fun updateProjectSettings(settingsBuilder: (ProjectSettings) -> ProjectSettings) {
        var updatedState: ConnectUiState? = null
        _uiState.update { current ->
            val device = findDeviceOrDefault(current.selectedContext.deviceId)
            val currentSettings = loadProjectSettings(device)
            val updatedSettings = settingsBuilder(currentSettings)
            projectSettingsStore.save(device.deviceId, updatedSettings)
            val reapplied = applyContextSelection(current, current.selectedContext)
            val shouldStop = reapplied.selectedContext != current.selectedContext ||
                reapplied.maxTrackForSelectedProfile != current.maxTrackForSelectedProfile
            val finalState = if (shouldStop) {
                reapplied.copy(nowPlaying = null, nowPlayingSignal = null)
            } else {
                reapplied
            }.copy(projectMessage = null)
            updatedState = finalState
            finalState
        }

        updatedState?.let { state ->
            usbSerialManager.setMockMaxTrack(state.maxTrackForSelectedProfile)
            persistSelectedContext(state.selectedContext)
            if (state.nowPlaying == null) {
                audioEngine.stop()
                applyBackgroundMusic(state)
            }
        }
    }

    private fun onPlayerEvent(event: PlayerEvent) {
        when (event) {
            is PlayerEvent.Touch -> {
                if (uiState.value.isExplorationMode) {
                    playExplorationSignal(event.index)
                } else {
                    playSignal(event.index)
                }
            }
            is PlayerEvent.Message -> Unit
            is PlayerEvent.Unknown -> Unit
        }
    }

    private fun playExplorationSignal(signalIndex: Int) {
        val state = uiState.value
        if (signalIndex !in 1..state.maxTrackForSelectedProfile) {
            return
        }

        val now = SystemClock.elapsedRealtime()
        val last = lastExplorationSignalAt[signalIndex] ?: 0L
        if (now - last < EXPLORATION_SIGNAL_COOLDOWN_MS) {
            return
        }

        lastExplorationSignalAt[signalIndex] = now
        runCatching {
            audioEngine.playEffectAsset(DEFAULT_EXPLORATION_SIGNAL_ASSET_PATH)
            resetArduinoActiveTrack("exploration T$signalIndex")
            appendLog("PLAY: exploration signal T$signalIndex")
        }.onFailure { throwable ->
            appendLog("ERR: exploration signal ${throwable.message ?: "unknown"}")
        }
    }

    private fun playSignal(signalIndex: Int) {
        idleJob?.cancel()
        val state = uiState.value
        if (signalIndex !in 1..state.maxTrackForSelectedProfile) {
            appendLog("ERR: signal out of range T$signalIndex")
            return
        }

        val currentNowPlayingSignal = state.nowPlayingSignal
        if (currentNowPlayingSignal == signalIndex) {
            return
        }

        val trackKey = resolveTrackForSignal(signalIndex, state)
        if (trackKey.isBlank()) {
            appendLog("ERR: invalid track signal=T$signalIndex track=$trackKey")
            return
        }

        val context = state.selectedContext
        val assetPath = TrackResolver.resolve(trackKey = trackKey, ctx = context)
        if (assetPath == null) {
            appendLog("ERR: no track resolved for signal=$signalIndex track=$trackKey")
            return
        }

        runCatching {
            audioEngine.setMainVolume(resolveVolumeForSignal(signalIndex, state) / 100f)
            audioEngine.playAsset(assetPath)
            _uiState.update {
                it.copy(
                    nowPlaying = signalIndex,
                    nowPlayingSignal = signalIndex,
                    isIdlePlaying = false
                )
            }
            applyBackgroundMusic(uiState.value)
            appendLog("PLAY: T$signalIndex -> $trackKey")
        }.onFailure { throwable ->
            appendLog("ERR: playTrack ${throwable.message ?: "unknown"}")
        }
    }

    private fun tryUpdateThresholdFromMessage(message: String) {
        val match = THRESHOLD_ACK_REGEX.find(message) ?: return
        val parsed = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return
        onThresholdChanged(parsed)
    }

    private fun createInitialState(initialContext: PlaybackContext): ConnectUiState {
        val seed = ConnectUiState(devices = deviceOptions)
        return applyContextSelection(seed, initialContext)
    }

    private fun applyContextSelection(state: ConnectUiState, requested: PlaybackContext): ConnectUiState {
        val resolvedContext = resolveContext(requested)
        val selectedDevice = findDeviceOrDefault(resolvedContext.deviceId)
        val projectSettings = loadProjectSettings(selectedDevice)
        val idleSettings = idleSettingsStore.load(selectedDevice.deviceId)
        val idleTrackSettings = idleSettingsStore.loadTrackSettings(resolvedContext)
        val backgroundMusicSettings = backgroundMusicSettingsStore.load(selectedDevice.deviceId)
        val explorationSettings = explorationSettingsStore.load(selectedDevice.deviceId)
        val explorationTrackSettings = explorationSettingsStore.loadTrackSettings(resolvedContext)
        val projectSettingsUi = buildProjectSettingsUi(selectedDevice, projectSettings)
        val projectProfiles = buildProjectProfiles(selectedDevice, projectSettings)
        val enabledSeries = projectSettings.enabledSeries
        val availableProfiles = projectProfiles
            .filter { profile ->
                profile.languages.any { language ->
                    enabledSeries.contains(ProjectSettingsStore.seriesKey(profile.profileId, language))
                }
            }
            .map { it.profileId }
        val selectedProfile = projectProfiles.firstOrNull { it.profileId == resolvedContext.profile }
            ?: projectProfiles.firstOrNull()
        val availableLanguages = selectedProfile
            ?.let { profile ->
                profile.languages.filter { language ->
                    enabledSeries.contains(ProjectSettingsStore.seriesKey(profile.profileId, language))
                }
            }
            ?.takeIf { it.isNotEmpty() }
            ?: listOf(PlaybackContext.DEFAULT_LANGUAGE)
        val maxTrack = getMaxTrackForProjectProfile(projectSettings)
        val signalTrackMappings = loadSignalTrackMappings(resolvedContext, maxTrack)
        val userAudioTracks = loadUserAudioTracks(resolvedContext, maxTrack, signalTrackMappings)
        val availableAudioTracks = buildAvailableAudioTracks(userAudioTracks, maxTrack)

        return state.copy(
            devices = deviceOptions,
            availableProfiles = availableProfiles,
            availableLanguages = availableLanguages,
            selectedContext = resolvedContext,
            selectedDeviceDisplayName = projectSettings.culturalAssetName.ifBlank { selectedDevice.displayName },
            selectedDeviceCredit = projectSettings.schoolName.ifBlank { selectedDevice.credit },
            contextLabel = formatContextLabel(resolvedContext),
            maxTrackForSelectedProfile = maxTrack,
            signalTrackMappings = signalTrackMappings,
            projectSettings = projectSettingsUi,
            userAudioTracks = userAudioTracks,
            availableAudioTracks = availableAudioTracks,
            idleSettings = IdleSettingsUi(
                timeoutMinutes = idleSettings.timeoutMinutes,
                useDefaultTrack = idleTrackSettings.useDefaultTrack,
                customTrackKey = idleTrackSettings.customTrackKey
            ),
            backgroundMusicSettings = BackgroundMusicSettingsUi(
                enabled = backgroundMusicSettings.enabled,
                useDefaultTrack = backgroundMusicSettings.useDefaultTrack,
                customTrackKey = backgroundMusicSettings.customTrackKey,
                onlyDuringTracks = backgroundMusicSettings.onlyDuringTracks,
                fullVolumePercent = backgroundMusicSettings.fullVolumePercent,
                duckedVolumePercent = backgroundMusicSettings.duckedVolumePercent
            ),
            explorationSettings = ExplorationSettingsUi(
                timeoutMinutes = explorationSettings.timeoutMinutes,
                useDefaultTrack = explorationTrackSettings.useDefaultTrack,
                customTrackKey = explorationTrackSettings.customTrackKey
            )
        )
    }

    private fun resolveContext(requested: PlaybackContext): PlaybackContext {
        val device = findDeviceOrDefault(requested.deviceId)
        val projectSettings = loadProjectSettings(device)
        val projectProfiles = buildProjectProfiles(device, projectSettings)
        val profileIds = projectProfiles.map { it.profileId }
        val enabledSeries = projectSettings.enabledSeries
        val enabledProfileIds = projectProfiles
            .filter { profile ->
                profile.languages.any { language ->
                    enabledSeries.contains(ProjectSettingsStore.seriesKey(profile.profileId, language))
                }
            }
            .map { it.profileId }
        val candidateProfiles = enabledProfileIds.ifEmpty { profileIds }

        val resolvedProfile = when {
            candidateProfiles.contains(requested.profile) -> requested.profile
            candidateProfiles.contains(DeviceCatalog.PROFILE_ADULT) -> DeviceCatalog.PROFILE_ADULT
            candidateProfiles.isNotEmpty() -> candidateProfiles.first()
            else -> PlaybackContext.DEFAULT_PROFILE
        }

        val languagesForProfile = projectProfiles
            .firstOrNull { it.profileId == resolvedProfile }
            ?.languages
            .orEmpty()
        val enabledLanguagesForProfile = languagesForProfile.filter { language ->
            enabledSeries.contains(ProjectSettingsStore.seriesKey(resolvedProfile, language))
        }
        val candidateLanguages = enabledLanguagesForProfile.ifEmpty { languagesForProfile }

        val resolvedLanguage = when {
            candidateLanguages.contains(requested.language) -> requested.language
            candidateLanguages.contains(DeviceCatalog.LANGUAGE_IT) -> DeviceCatalog.LANGUAGE_IT
            candidateLanguages.isNotEmpty() -> candidateLanguages.first()
            else -> PlaybackContext.DEFAULT_LANGUAGE
        }

        return PlaybackContext(
            deviceId = device.deviceId,
            profile = resolvedProfile,
            language = resolvedLanguage
        )
    }

    private fun findDeviceOrDefault(deviceId: String): DeviceDefinition {
        return DeviceCatalog.findDevice(deviceId)
            ?: DeviceCatalog.devices.first()
    }

    private fun persistSelectedContext(context: PlaybackContext) {
        playbackSelectionStore.save(context)
    }

    private fun appendLog(line: String) {
        serialLogBuffer.append(line)
    }

    private fun resolveTrackForSignal(signalIndex: Int, state: ConnectUiState): String {
        val mappedTrack = state.signalTrackMappings
            .firstOrNull { it.signalIndex == signalIndex }
            ?.trackKey
        return mappedTrack ?: "%02d.mp3".format(signalIndex)
    }

    private fun resolveVolumeForSignal(signalIndex: Int, state: ConnectUiState): Int {
        return state.signalTrackMappings
            .firstOrNull { it.signalIndex == signalIndex }
            ?.volumePercent
            ?.coerceIn(MIN_TRACK_VOLUME_PERCENT, MAX_TRACK_VOLUME_PERCENT)
            ?: MAX_TRACK_VOLUME_PERCENT
    }

    private fun loadSignalTrackMappings(
        context: PlaybackContext,
        maxTrack: Int
    ): List<SignalTrackMappingUi> {
        val labels = signalMappingStore.loadLabels(context, maxTrack)
        val volumes = signalMappingStore.loadVolumes(context, maxTrack)
        return signalMappingStore.load(context, maxTrack)
            .toSortedMap()
            .map { (signalIndex, trackKey) ->
                SignalTrackMappingUi(
                    signalIndex = signalIndex,
                    trackKey = trackKey,
                    label = labels[signalIndex].orEmpty(),
                    volumePercent = volumes[signalIndex] ?: MAX_TRACK_VOLUME_PERCENT
                )
            }
    }

    private fun loadUserAudioTracks(
        context: PlaybackContext,
        maxTrack: Int,
        signalMappings: List<SignalTrackMappingUi>
    ): List<UserAudioTrackUi> {
        return userAudioStore.listTracks(context).map { track ->
            val associatedSignals = signalMappings
                .filter { mapping -> mapping.trackKey == track.trackKey }
                .map { mapping -> mapping.signalIndex }
            UserAudioTrackUi(
                trackKey = track.trackKey,
                trackId = track.trackId,
                fileName = track.fileName,
                relativePath = track.relativePath,
                sourceLabel = track.sourceLabel,
                associatedSignals = associatedSignals,
                canRemove = true,
                inProjectRange = track.trackId?.let { it in 1..maxTrack } ?: true
            )
        }
    }

    private fun buildAvailableAudioTracks(
        loadedTracks: List<UserAudioTrackUi>,
        maxTrack: Int
    ): List<UserAudioTrackUi> {
        return loadedTracks
            .distinctBy { it.trackKey }
            .sortedWith(
                compareBy<UserAudioTrackUi> { it.trackId ?: Int.MAX_VALUE }
                    .thenBy { it.fileName }
            )
    }

    private fun loadProjectSettings(device: DeviceDefinition): ProjectSettings {
        return projectSettingsStore.load(device)
    }

    private fun buildProjectSettingsUi(
        device: DeviceDefinition,
        settings: ProjectSettings
    ): ProjectSettingsUi {
        val series = buildProjectProfiles(device, settings).flatMap { profile ->
            profile.languages.map { language ->
                ProjectSeriesUi(
                    profileId = profile.profileId,
                    language = language,
                    enabled = settings.enabledSeries.contains(
                        ProjectSettingsStore.seriesKey(profile.profileId, language)
                    ),
                    canRemove = settings.customLanguagesByProfile[profile.profileId]
                        .orEmpty()
                        .contains(language)
                )
            }
        }

        return ProjectSettingsUi(
            culturalAssetName = settings.culturalAssetName,
            schoolName = settings.schoolName,
            trackCount = settings.trackCount,
            profileLabels = settings.profileLabels,
            languageLabels = settings.languageLabels,
            series = series,
            removableProfiles = settings.customProfiles
        )
    }

    private fun buildProjectProfiles(
        device: DeviceDefinition,
        settings: ProjectSettings
    ): List<DeviceProfileDefinition> {
        val projectTrackCount = settings.trackCount.coerceIn(
            ProjectSettingsStore.MIN_TRACK_COUNT,
            ProjectSettingsStore.MAX_TRACK_COUNT
        )
        val defaultProfiles = device.profiles.map { profile ->
            profile.copy(
                languages = (profile.languages + settings.customLanguagesByProfile[profile.profileId].orEmpty())
                    .distinct(),
                trackCount = projectTrackCount
            )
        }
        val customProfiles = settings.customProfiles
            .sorted()
            .map { profileId ->
                DeviceProfileDefinition(
                    profileId = profileId,
                    languages = settings.customLanguagesByProfile[profileId]
                        .orEmpty()
                        .toList()
                        .sorted(),
                    trackCount = projectTrackCount
                )
            }
        return defaultProfiles + customProfiles
    }

    private fun getMaxTrackForProjectProfile(settings: ProjectSettings): Int {
        return settings.trackCount.coerceIn(
            ProjectSettingsStore.MIN_TRACK_COUNT,
            ProjectSettingsStore.MAX_TRACK_COUNT
        )
    }

    private fun seriesKeysForProfile(
        profileId: String,
        device: DeviceDefinition,
        settings: ProjectSettings
    ): Set<String> {
        return buildProjectProfiles(device, settings)
            .firstOrNull { it.profileId == profileId }
            ?.languages
            ?.map { language -> ProjectSettingsStore.seriesKey(profileId, language) }
            ?.toSet()
            .orEmpty()
    }

    private fun remainingLanguageIds(
        device: DeviceDefinition,
        customLanguagesByProfile: Map<String, Set<String>>
    ): Set<String> {
        return device.profiles
            .flatMap { it.languages }
            .plus(customLanguagesByProfile.values.flatten())
            .toSet()
    }

    private fun resolveLanguageInput(label: String, settings: ProjectSettings): LanguageInput {
        val knownLanguage = LANGUAGE_ALIASES[compactLanguageKey(label)]

        if (knownLanguage != null) {
            return LanguageInput(
                languageId = knownLanguage,
                displayLabel = settings.languageLabels[knownLanguage]
                    ?: ProjectSettingsStore.defaultLanguageLabel(knownLanguage)
            )
        }

        return LanguageInput(
            languageId = nextCustomId(
                prefix = "custom_lang",
                existingIds = settings.languageLabels.keys
            ),
            displayLabel = label.trim()
        )
    }

    private fun normalizeLanguageText(value: String): String {
        val lower = value.trim().lowercase(Locale.ROOT)
        return Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
    }

    private fun compactLanguageKey(value: String): String {
        return normalizeLanguageText(value)
            .replace(" ", "")
            .replace("_", "")
            .replace("-", "")
    }

    private fun showProjectMessage(message: String) {
        _uiState.update { it.copy(projectMessage = message) }
    }

    private fun nextCustomId(prefix: String, existingIds: Set<String>): String {
        var index = 1
        var candidate = "${prefix}_$index"
        while (existingIds.contains(candidate)) {
            index++
            candidate = "${prefix}_$index"
        }
        return candidate
    }

    private fun formatContextLabel(context: PlaybackContext): String {
        return "${context.deviceId} | ${context.profile} | ${context.language}"
    }

    companion object {
        private const val THRESHOLD_MIN = 1000
        private const val THRESHOLD_MAX = 20000
        private const val MIN_TRACK_VOLUME_PERCENT = 10
        private const val MAX_TRACK_VOLUME_PERCENT = 100
        private const val DEFAULT_IDLE_ASSET_PATH = "audio/idle/default/idle.mp3"
        private const val DEFAULT_BACKGROUND_ASSET_PATH = "audio/background/default/santelena.mp3"
        private const val DEFAULT_EXPLORATION_INTRO_ASSET_PATH = "audio/exploration/default/m.wav"
        private const val DEFAULT_EXPLORATION_SIGNAL_ASSET_PATH = "audio/exploration/default/s.wav"
        private const val EXPLORATION_SIGNAL_COOLDOWN_MS = 600L
        private val THRESHOLD_ACK_REGEX = Regex(
            pattern = """(?i)^Soglia salvata a\s+(\d+)$"""
        )
        private val LANGUAGE_ALIASES = mapOf(
            "it" to DeviceCatalog.LANGUAGE_IT,
            "ita" to DeviceCatalog.LANGUAGE_IT,
            "italiano" to DeviceCatalog.LANGUAGE_IT,
            "italiana" to DeviceCatalog.LANGUAGE_IT,
            "italian" to DeviceCatalog.LANGUAGE_IT,
            "en" to DeviceCatalog.LANGUAGE_EN,
            "eng" to DeviceCatalog.LANGUAGE_EN,
            "inglese" to DeviceCatalog.LANGUAGE_EN,
            "english" to DeviceCatalog.LANGUAGE_EN,
            "es" to DeviceCatalog.LANGUAGE_ES,
            "esp" to DeviceCatalog.LANGUAGE_ES,
            "spagnolo" to DeviceCatalog.LANGUAGE_ES,
            "spanish" to DeviceCatalog.LANGUAGE_ES,
            "espanol" to DeviceCatalog.LANGUAGE_ES,
            "fr" to "fr",
            "fra" to "fr",
            "fre" to "fr",
            "francese" to "fr",
            "frances" to "fr",
            "francais" to "fr",
            "french" to "fr",
            "de" to "de",
            "deu" to "de",
            "ger" to "de",
            "tedesco" to "de",
            "german" to "de",
            "deutsch" to "de",
            "pt" to "pt",
            "por" to "pt",
            "portoghese" to "pt",
            "portugues" to "pt",
            "portuguese" to "pt",
            "nl" to "nl",
            "nld" to "nl",
            "dut" to "nl",
            "olandese" to "nl",
            "neerlandese" to "nl",
            "dutch" to "nl",
            "ru" to "ru",
            "rus" to "ru",
            "russo" to "ru",
            "russian" to "ru",
            "uk" to "uk",
            "ukr" to "uk",
            "ucraino" to "uk",
            "ukrainian" to "uk",
            "pl" to "pl",
            "pol" to "pl",
            "polacco" to "pl",
            "polish" to "pl",
            "ro" to "ro",
            "ron" to "ro",
            "rum" to "ro",
            "rumeno" to "ro",
            "romeno" to "ro",
            "romanian" to "ro",
            "el" to "el",
            "ell" to "el",
            "gre" to "el",
            "greco" to "el",
            "greek" to "el",
            "tr" to "tr",
            "tur" to "tr",
            "turco" to "tr",
            "turkish" to "tr",
            "ar" to "ar",
            "ara" to "ar",
            "arabo" to "ar",
            "arabic" to "ar",
            "zh" to "zh",
            "zho" to "zh",
            "chi" to "zh",
            "cinese" to "zh",
            "chinese" to "zh",
            "ja" to "ja",
            "jpn" to "ja",
            "giapponese" to "ja",
            "japanese" to "ja",
            "ko" to "ko",
            "kor" to "ko",
            "coreano" to "ko",
            "korean" to "ko",
            "hi" to "hi",
            "hin" to "hi",
            "hindi" to "hi",
            "bn" to "bn",
            "ben" to "bn",
            "bengalese" to "bn",
            "bengali" to "bn",
            "bangla" to "bn",
            "ur" to "ur",
            "urd" to "ur",
            "urdu" to "ur",
            "pa" to "pa",
            "pan" to "pa",
            "punjabi" to "pa",
            "pangabi" to "pa",
            "ps" to "ps",
            "pus" to "ps",
            "pashto" to "ps",
            "pashtu" to "ps",
            "sd" to "sd",
            "snd" to "sd",
            "sindhi" to "sd",
            "ta" to "ta",
            "tam" to "ta",
            "tamil" to "ta",
            "te" to "te",
            "tel" to "te",
            "telugu" to "te",
            "ml" to "ml",
            "mal" to "ml",
            "malayalam" to "ml",
            "gu" to "gu",
            "guj" to "gu",
            "gujarati" to "gu",
            "mr" to "mr",
            "mar" to "mr",
            "marathi" to "mr",
            "fa" to "fa",
            "fas" to "fa",
            "per" to "fa",
            "persiano" to "fa",
            "persian" to "fa",
            "farsi" to "fa",
            "dari" to "fa",
            "hazaragi" to "fa",
            "hazara" to "fa",
            "uz" to "uz",
            "uzb" to "uz",
            "uzbeco" to "uz",
            "uzbeko" to "uz",
            "uzbek" to "uz",
            "tk" to "tk",
            "tuk" to "tk",
            "turkmeno" to "tk",
            "turkmen" to "tk",
            "he" to "he",
            "heb" to "he",
            "ebraico" to "he",
            "hebrew" to "he",
            "sv" to "sv",
            "swe" to "sv",
            "svedese" to "sv",
            "swedish" to "sv",
            "no" to "no",
            "nor" to "no",
            "norvegese" to "no",
            "norwegian" to "no",
            "da" to "da",
            "dan" to "da",
            "danese" to "da",
            "danish" to "da",
            "fi" to "fi",
            "fin" to "fi",
            "finlandese" to "fi",
            "finnish" to "fi",
            "cs" to "cs",
            "ces" to "cs",
            "cze" to "cs",
            "ceco" to "cs",
            "czech" to "cs",
            "sk" to "sk",
            "slk" to "sk",
            "slo" to "sk",
            "slovacco" to "sk",
            "slovak" to "sk",
            "sl" to "sl",
            "slv" to "sl",
            "sloveno" to "sl",
            "slovenian" to "sl",
            "hr" to "hr",
            "hrv" to "hr",
            "croato" to "hr",
            "croatian" to "hr",
            "sr" to "sr",
            "srp" to "sr",
            "serbo" to "sr",
            "serbian" to "sr",
            "bg" to "bg",
            "bul" to "bg",
            "bulgaro" to "bg",
            "bulgarian" to "bg",
            "hu" to "hu",
            "hun" to "hu",
            "ungherese" to "hu",
            "hungarian" to "hu",
            "ca" to "ca",
            "cat" to "ca",
            "catalano" to "ca",
            "catalan" to "ca",
            "eu" to "eu",
            "eus" to "eu",
            "baq" to "eu",
            "basco" to "eu",
            "basque" to "eu",
            "gl" to "gl",
            "glg" to "gl",
            "galiziano" to "gl",
            "galician" to "gl",
            "ga" to "ga",
            "gle" to "ga",
            "irlandese" to "ga",
            "irish" to "ga",
            "mt" to "mt",
            "mlt" to "mt",
            "maltese" to "mt",
            "et" to "et",
            "est" to "et",
            "estone" to "et",
            "estonian" to "et",
            "lv" to "lv",
            "lav" to "lv",
            "lettone" to "lv",
            "latvian" to "lv",
            "lt" to "lt",
            "lit" to "lt",
            "lituano" to "lt",
            "lithuanian" to "lt",
            "sq" to "sq",
            "sqi" to "sq",
            "alb" to "sq",
            "albanese" to "sq",
            "albanian" to "sq",
            "mk" to "mk",
            "mkd" to "mk",
            "macedone" to "mk",
            "macedonian" to "mk",
            "hy" to "hy",
            "hye" to "hy",
            "arm" to "hy",
            "armeno" to "hy",
            "armenian" to "hy",
            "ka" to "ka",
            "kat" to "ka",
            "geo" to "ka",
            "georgiano" to "ka",
            "georgian" to "ka",
            "vi" to "vi",
            "vie" to "vi",
            "vietnamita" to "vi",
            "vietnamese" to "vi",
            "th" to "th",
            "tha" to "th",
            "thai" to "th",
            "thailandese" to "th",
            "id" to "id",
            "ind" to "id",
            "indonesiano" to "id",
            "indonesian" to "id",
            "ms" to "ms",
            "msa" to "ms",
            "may" to "ms",
            "malese" to "ms",
            "malay" to "ms",
            "sw" to "sw",
            "swa" to "sw",
            "swahili" to "sw",
            "af" to "af",
            "afr" to "af",
            "afrikaans" to "af"
        )
    }
}

private data class LanguageInput(
    val languageId: String,
    val displayLabel: String
)
