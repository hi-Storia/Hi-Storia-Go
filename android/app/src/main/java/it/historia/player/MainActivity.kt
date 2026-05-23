package it.historia.player

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import it.historia.player.data.audio.Media3AudioEngine
import it.historia.player.data.audio.UserAudioStore
import it.historia.player.data.preferences.BackgroundMusicSettingsStore
import it.historia.player.data.preferences.ExplorationSettingsStore
import it.historia.player.data.preferences.IdleSettingsStore
import it.historia.player.data.preferences.PlaybackSelectionStore
import it.historia.player.data.preferences.ProjectSettingsStore
import it.historia.player.data.preferences.SignalMappingStore
import it.historia.player.data.usb.UsbSerialManager
import it.historia.player.data.util.SerialLogBuffer
import it.historia.player.ui.screens.connect.ConnectScreen
import it.historia.player.ui.screens.connect.ConnectViewModel
import it.historia.player.ui.screens.connect.ConnectViewModelFactory
import it.historia.player.ui.screens.debug.AdminScreen
import it.historia.player.ui.screens.project.ProjectScreen

private val AUDIO_DOCUMENT_MIME_TYPES = arrayOf("audio/*", "application/octet-stream")

class MainActivity : ComponentActivity() {
    private val serialLogBuffer: SerialLogBuffer by lazy { SerialLogBuffer() }

    private val connectViewModel: ConnectViewModel by viewModels {
        ConnectViewModelFactory(
            usbSerialManager = UsbSerialManager(applicationContext, serialLogBuffer),
            audioEngine = Media3AudioEngine(applicationContext),
            userAudioStore = UserAudioStore(applicationContext),
            backgroundMusicSettingsStore = BackgroundMusicSettingsStore(applicationContext),
            explorationSettingsStore = ExplorationSettingsStore(applicationContext),
            idleSettingsStore = IdleSettingsStore(applicationContext),
            playbackSelectionStore = PlaybackSelectionStore(applicationContext),
            projectSettingsStore = ProjectSettingsStore(applicationContext),
            signalMappingStore = SignalMappingStore(applicationContext),
            serialLogBuffer = serialLogBuffer
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            MaterialTheme {
                val navController = rememberNavController()
                val state = connectViewModel.uiState.collectAsStateWithLifecycle()
                var pendingImportTrackId by remember { mutableIntStateOf(0) }
                val audioPicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    connectViewModel.onAudioFileSelected(pendingImportTrackId, uri)
                    pendingImportTrackId = 0
                }
                val idleAudioPicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    connectViewModel.onIdleAudioFileSelected(uri)
                }
                val explorationAudioPicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    connectViewModel.onExplorationAudioFileSelected(uri)
                }
                val backgroundAudioPicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    connectViewModel.onBackgroundMusicFileSelected(uri)
                }

                NavHost(
                    navController = navController,
                    startDestination = "player"
                ) {
                    composable("player") {
                        ConnectScreen(
                            state = state.value,
                            onStopClick = connectViewModel::stopPlayback,
                            onStartExplorationClick = connectViewModel::startExplorationMode,
                            onStopExplorationClick = connectViewModel::stopExplorationMode,
                            onProfileSelected = connectViewModel::onProfileSelected,
                            onLanguageSelected = connectViewModel::onLanguageSelected,
                            onOpenDebug = {
                                navController.navigate("debug") {
                                    launchSingleTop = true
                                }
                            }
                        )
                    }
                    composable("project") {
                        ProjectScreen(
                            state = state.value,
                            onBackClick = { navController.navigateUp() },
                            onProjectNameChanged = connectViewModel::onProjectNameChanged,
                            onProjectSchoolChanged = connectViewModel::onProjectSchoolChanged,
                            onProjectTrackCountChanged = connectViewModel::onProjectTrackCountChanged,
                            onProfileLabelChanged = connectViewModel::onProjectProfileLabelChanged,
                            onLanguageLabelChanged = connectViewModel::onProjectLanguageLabelChanged,
                            onSeriesEnabledChanged = connectViewModel::onProjectSeriesEnabledChanged,
                            onProfileEnabledChanged = connectViewModel::onProjectProfileEnabledChanged,
                            onLanguageAdded = connectViewModel::onProjectLanguageAdded,
                            onProfileAdded = connectViewModel::onProjectProfileAdded,
                            onProfileRemoved = connectViewModel::onProjectProfileRemoved,
                            onLanguageRemoved = connectViewModel::onProjectLanguageRemoved,
                            onProjectReset = connectViewModel::onProjectReset,
                            onProjectMessageDismissed = connectViewModel::onProjectMessageDismissed
                        )
                    }
                    composable("debug") {
                        AdminScreen(
                            state = state.value,
                            onBackClick = { navController.navigateUp() },
                            onThresholdChanged = connectViewModel::onThresholdChanged,
                            onSendThreshold = connectViewModel::sendSetThreshold,
                            onPlaySignal = connectViewModel::playSignalFromAdmin,
                            onSignalTrackSelected = connectViewModel::onSignalTrackSelected,
                            onSignalLabelSaved = connectViewModel::onSignalLabelSaved,
                            onSignalVolumeChanged = connectViewModel::onSignalVolumeChanged,
                            onOpenProject = {
                                navController.navigate("project") {
                                    launchSingleTop = true
                                }
                            },
                            onStopPlayback = connectViewModel::stopPlayback,
                            onImportTrack = { trackId ->
                                pendingImportTrackId = trackId
                                audioPicker.launch(AUDIO_DOCUMENT_MIME_TYPES)
                            },
                            onRemoveAudioTrack = connectViewModel::onAudioTrackRemoved,
                            onCleanUnusedAudioTracks = connectViewModel::onUnusedAudioTracksCleaned,
                            onImportIdleTrack = {
                                idleAudioPicker.launch(AUDIO_DOCUMENT_MIME_TYPES)
                            },
                            onImportExplorationTrack = {
                                explorationAudioPicker.launch(AUDIO_DOCUMENT_MIME_TYPES)
                            },
                            onImportBackgroundMusic = {
                                backgroundAudioPicker.launch(AUDIO_DOCUMENT_MIME_TYPES)
                            },
                            onIdleTimeoutChanged = connectViewModel::onIdleTimeoutChanged,
                            onIdleUseDefaultTrackChanged = connectViewModel::onIdleUseDefaultTrackChanged,
                            onExplorationUseDefaultTrackChanged = connectViewModel::onExplorationUseDefaultTrackChanged,
                            onBackgroundMusicEnabledChanged = connectViewModel::onBackgroundMusicEnabledChanged,
                            onBackgroundMusicUseDefaultChanged = connectViewModel::onBackgroundMusicUseDefaultChanged,
                            onBackgroundMusicOnlyDuringTracksChanged =
                                connectViewModel::onBackgroundMusicOnlyDuringTracksChanged,
                            onBackgroundMusicFullVolumeChanged = connectViewModel::onBackgroundMusicFullVolumeChanged,
                            onBackgroundMusicDuckedVolumeChanged = connectViewModel::onBackgroundMusicDuckedVolumeChanged,
                            onExplorationTimeoutChanged = connectViewModel::onExplorationTimeoutChanged,
                            onProjectMessageDismissed = connectViewModel::onProjectMessageDismissed
                        )
                    }
                }
            }
        }
    }
}
