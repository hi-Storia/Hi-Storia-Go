package it.historia.player.ui.screens.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import it.historia.player.data.audio.AudioEngine
import it.historia.player.data.audio.UserAudioStore
import it.historia.player.data.preferences.BackgroundMusicSettingsStore
import it.historia.player.data.preferences.ExplorationSettingsStore
import it.historia.player.data.preferences.IdleSettingsStore
import it.historia.player.data.preferences.PlaybackSelectionStore
import it.historia.player.data.preferences.ProjectSettingsStore
import it.historia.player.data.preferences.SignalMappingStore
import it.historia.player.data.usb.UsbSerialManager
import it.historia.player.data.util.SerialLogBuffer

class ConnectViewModelFactory(
    private val usbSerialManager: UsbSerialManager,
    private val audioEngine: AudioEngine,
    private val userAudioStore: UserAudioStore,
    private val backgroundMusicSettingsStore: BackgroundMusicSettingsStore,
    private val explorationSettingsStore: ExplorationSettingsStore,
    private val idleSettingsStore: IdleSettingsStore,
    private val playbackSelectionStore: PlaybackSelectionStore,
    private val projectSettingsStore: ProjectSettingsStore,
    private val signalMappingStore: SignalMappingStore,
    private val serialLogBuffer: SerialLogBuffer
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ConnectViewModel::class.java)) {
            return ConnectViewModel(
                usbSerialManager = usbSerialManager,
                audioEngine = audioEngine,
                userAudioStore = userAudioStore,
                backgroundMusicSettingsStore = backgroundMusicSettingsStore,
                explorationSettingsStore = explorationSettingsStore,
                idleSettingsStore = idleSettingsStore,
                playbackSelectionStore = playbackSelectionStore,
                projectSettingsStore = projectSettingsStore,
                signalMappingStore = signalMappingStore,
                serialLogBuffer = serialLogBuffer
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
