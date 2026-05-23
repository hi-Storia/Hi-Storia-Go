package it.historia.player.ui.screens.debug

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import it.historia.player.ui.components.LogPanel
import it.historia.player.ui.screens.connect.ConnectUiState
import it.historia.player.ui.screens.connect.SignalTrackMappingUi
import it.historia.player.ui.screens.connect.UserAudioTrackUi

const val DEBUG_SERIAL = true

@Composable
fun AdminScreen(
    state: ConnectUiState,
    onBackClick: () -> Unit,
    onThresholdChanged: (Int) -> Unit,
    onSendThreshold: () -> Unit,
    onPlaySignal: (Int) -> Unit,
    onSignalTrackSelected: (Int, String) -> Unit,
    onSignalLabelSaved: (Int, String) -> Unit,
    onSignalVolumeChanged: (Int, Int) -> Unit,
    onOpenProject: () -> Unit,
    onStopPlayback: () -> Unit,
    onImportTrack: (Int) -> Unit,
    onRemoveAudioTrack: (String) -> Unit,
    onCleanUnusedAudioTracks: () -> Unit,
    onImportIdleTrack: () -> Unit,
    onImportExplorationTrack: () -> Unit,
    onImportBackgroundMusic: () -> Unit,
    onIdleTimeoutChanged: (String) -> Unit,
    onIdleUseDefaultTrackChanged: (Boolean) -> Unit,
    onExplorationUseDefaultTrackChanged: (Boolean) -> Unit,
    onBackgroundMusicEnabledChanged: (Boolean) -> Unit,
    onBackgroundMusicUseDefaultChanged: (Boolean) -> Unit,
    onBackgroundMusicOnlyDuringTracksChanged: (Boolean) -> Unit,
    onBackgroundMusicFullVolumeChanged: (Int) -> Unit,
    onBackgroundMusicDuckedVolumeChanged: (Int) -> Unit,
    onExplorationTimeoutChanged: (String) -> Unit,
    onProjectMessageDismissed: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Board", "Contenuti", "Smartphone")

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Admin",
                style = MaterialTheme.typography.titleLarge
            )
            Button(onClick = onBackClick) {
                Text("Back")
            }
        }

        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, label ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(label) }
                )
            }
        }

        when (selectedTab) {
            0 -> DeviceAdminSection(
                state = state,
                onThresholdChanged = onThresholdChanged,
                onSendThreshold = onSendThreshold
            )

            1 -> ContentAdminSection(
                state = state,
                onPlaySignal = onPlaySignal,
                onSignalTrackSelected = onSignalTrackSelected,
                onSignalLabelSaved = onSignalLabelSaved,
                onSignalVolumeChanged = onSignalVolumeChanged,
                onOpenProject = onOpenProject,
                onStopPlayback = onStopPlayback,
                onImportTrack = onImportTrack,
                onRemoveAudioTrack = onRemoveAudioTrack,
                onCleanUnusedAudioTracks = onCleanUnusedAudioTracks,
                onImportIdleTrack = onImportIdleTrack,
                onImportExplorationTrack = onImportExplorationTrack,
                onImportBackgroundMusic = onImportBackgroundMusic,
                onIdleUseDefaultTrackChanged = onIdleUseDefaultTrackChanged,
                onExplorationUseDefaultTrackChanged = onExplorationUseDefaultTrackChanged,
                onBackgroundMusicEnabledChanged = onBackgroundMusicEnabledChanged,
                onBackgroundMusicUseDefaultChanged = onBackgroundMusicUseDefaultChanged,
                onBackgroundMusicOnlyDuringTracksChanged = onBackgroundMusicOnlyDuringTracksChanged
            )

            2 -> IdleAdminSection(
                state = state,
                onIdleTimeoutChanged = onIdleTimeoutChanged,
                onBackgroundMusicFullVolumeChanged = onBackgroundMusicFullVolumeChanged,
                onBackgroundMusicDuckedVolumeChanged = onBackgroundMusicDuckedVolumeChanged,
                onExplorationTimeoutChanged = onExplorationTimeoutChanged
            )
        }
    }

    state.projectMessage?.let { message ->
        AlertDialog(
            onDismissRequest = onProjectMessageDismissed,
            title = { Text("Contenuti") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = onProjectMessageDismissed) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun IdleAdminSection(
    state: ConnectUiState,
    onIdleTimeoutChanged: (String) -> Unit,
    onBackgroundMusicFullVolumeChanged: (Int) -> Unit,
    onBackgroundMusicDuckedVolumeChanged: (Int) -> Unit,
    onExplorationTimeoutChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Smartphone",
            style = MaterialTheme.typography.titleMedium
        )

        OutlinedTextField(
            value = state.idleSettings.timeoutMinutes.toString(),
            onValueChange = onIdleTimeoutChanged,
            label = { Text("Minuti di inattività") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = if (state.isIdlePlaying) "Idle in riproduzione" else "Idle in attesa",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider()

        Text(
            text = "Esplorazione",
            style = MaterialTheme.typography.titleMedium
        )

        OutlinedTextField(
            value = state.explorationSettings.timeoutMinutes.toString(),
            onValueChange = onExplorationTimeoutChanged,
            label = { Text("Minuti modalita esplorazione") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = if (state.isExplorationMode) "Esplorazione attiva" else "Esplorazione in attesa",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider()

        Text(
            text = "Musica",
            style = MaterialTheme.typography.titleMedium
        )

        if (!state.backgroundMusicSettings.enabled) {
            Text(
                text = "Musica OFF per questo progetto",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = "Volume massimo: ${state.backgroundMusicSettings.fullVolumePercent}%",
            style = MaterialTheme.typography.bodyMedium
        )
        Slider(
            value = state.backgroundMusicSettings.fullVolumePercent.toFloat(),
            onValueChange = { onBackgroundMusicFullVolumeChanged(it.toInt()) },
            valueRange = 0f..100f,
            enabled = state.backgroundMusicSettings.enabled &&
                !state.backgroundMusicSettings.onlyDuringTracks,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "Volume sotto traccia: ${state.backgroundMusicSettings.duckedVolumePercent}%",
            style = MaterialTheme.typography.bodyMedium
        )
        Slider(
            value = state.backgroundMusicSettings.duckedVolumePercent.toFloat(),
            onValueChange = { onBackgroundMusicDuckedVolumeChanged(it.toInt()) },
            valueRange = 0f..100f,
            enabled = state.backgroundMusicSettings.enabled,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun DeviceAdminSection(
    state: ConnectUiState,
    onThresholdChanged: (Int) -> Unit,
    onSendThreshold: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "USB: ${state.usbConnectionState}",
            style = MaterialTheme.typography.titleMedium
        )

        if (state.userMessage != null) {
            Text(
                text = state.userMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Text(
            text = "Soglia Touch (Arduino)",
            style = MaterialTheme.typography.titleMedium
        )

        Slider(
            value = state.currentThresholdUi.toFloat(),
            onValueChange = { onThresholdChanged(it.toInt()) },
            valueRange = 1000f..20000f,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = { onThresholdChanged(state.currentThresholdUi - 100) }) {
                Text("-")
            }
            Text(
                text = "${state.currentThresholdUi}",
                style = MaterialTheme.typography.titleMedium
            )
            Button(onClick = { onThresholdChanged(state.currentThresholdUi + 100) }) {
                Text("+")
            }
            Button(onClick = onSendThreshold) {
                Text("Invia")
            }
        }

        if (DEBUG_SERIAL) {
            LogPanel(
                lines = state.serialLogs,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ContentAdminSection(
    state: ConnectUiState,
    onPlaySignal: (Int) -> Unit,
    onSignalTrackSelected: (Int, String) -> Unit,
    onSignalLabelSaved: (Int, String) -> Unit,
    onSignalVolumeChanged: (Int, Int) -> Unit,
    onOpenProject: () -> Unit,
    onStopPlayback: () -> Unit,
    onImportTrack: (Int) -> Unit,
    onRemoveAudioTrack: (String) -> Unit,
    onCleanUnusedAudioTracks: () -> Unit,
    onImportIdleTrack: () -> Unit,
    onImportExplorationTrack: () -> Unit,
    onImportBackgroundMusic: () -> Unit,
    onIdleUseDefaultTrackChanged: (Boolean) -> Unit,
    onExplorationUseDefaultTrackChanged: (Boolean) -> Unit,
    onBackgroundMusicEnabledChanged: (Boolean) -> Unit,
    onBackgroundMusicUseDefaultChanged: (Boolean) -> Unit,
    onBackgroundMusicOnlyDuringTracksChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = state.selectedDeviceDisplayName,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = state.contextLabel,
            style = MaterialTheme.typography.bodyMedium
        )

        Button(
            onClick = onOpenProject,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Modifica progetto")
        }

        HorizontalDivider()

        Text(
            text = "Musica",
            style = MaterialTheme.typography.titleMedium
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(
                checked = state.backgroundMusicSettings.enabled,
                onCheckedChange = onBackgroundMusicEnabledChanged
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Musica ON",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = if (state.backgroundMusicSettings.enabled) {
                        "Attiva quando non ci sono tracce in play"
                    } else {
                        "Spenta per questo progetto"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(
                checked = state.backgroundMusicSettings.onlyDuringTracks,
                onCheckedChange = onBackgroundMusicOnlyDuringTracksChanged,
                enabled = state.backgroundMusicSettings.enabled
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Tieni musica solo nelle tracce",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "La musica parte solo bassa sotto una traccia audio.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(
                checked = state.backgroundMusicSettings.useDefaultTrack,
                onCheckedChange = onBackgroundMusicUseDefaultChanged,
                enabled = state.backgroundMusicSettings.enabled
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Usa musica default",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = state.backgroundMusicSettings.customTrackKey
                        ?: "audio/background/default/santelena.mp3",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        OutlinedButton(
            onClick = onImportBackgroundMusic,
            enabled = state.backgroundMusicSettings.enabled &&
                !state.backgroundMusicSettings.useDefaultTrack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Carica musica")
        }

        HorizontalDivider()

        Text(
            text = "Traccia 0",
            style = MaterialTheme.typography.titleMedium
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(
                checked = state.idleSettings.useDefaultTrack,
                onCheckedChange = onIdleUseDefaultTrackChanged
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Usa traccia default",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = state.idleSettings.customTrackKey ?: "audio/idle/default/idle.mp3",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        OutlinedButton(
            onClick = onImportIdleTrack,
            enabled = !state.idleSettings.useDefaultTrack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Carica traccia 0")
        }

        HorizontalDivider()

        Text(
            text = "Esplorazione",
            style = MaterialTheme.typography.titleMedium
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(
                checked = state.explorationSettings.useDefaultTrack,
                onCheckedChange = onExplorationUseDefaultTrackChanged
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Usa traccia default",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = state.explorationSettings.customTrackKey
                        ?: "audio/exploration/default/m.wav",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        OutlinedButton(
            onClick = onImportExplorationTrack,
            enabled = !state.explorationSettings.useDefaultTrack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Carica traccia esplorazione")
        }

        HorizontalDivider()

        Text(
            text = "Tocco lungo sul titolo per modificare il nome",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        state.signalTrackMappings.forEach { mapping ->
            SignalMappingRow(
                mapping = mapping,
                maxTrack = state.maxTrackForSelectedProfile,
                availableTracks = state.availableAudioTracks,
                isPlaying = state.nowPlayingSignal == mapping.signalIndex,
                onPlaySignal = onPlaySignal,
                onSignalTrackSelected = onSignalTrackSelected,
                onSignalLabelSaved = onSignalLabelSaved,
                onSignalVolumeChanged = onSignalVolumeChanged,
                onStopPlayback = onStopPlayback,
                onImportTrack = onImportTrack
            )
        }

        if (state.userAudioTracks.isNotEmpty()) {
            HorizontalDivider()

            Text(
                text = "File audio disponibili",
                style = MaterialTheme.typography.titleMedium
            )

            if (state.userAudioTracks.any {
                    it.sourceLabel == "caricato" && it.associatedSignals.isEmpty()
                }
            ) {
                OutlinedButton(
                    onClick = onCleanUnusedAudioTracks,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Pulisci non associati")
                }
            }

            state.userAudioTracks.forEach { track ->
                UserAudioTrackRow(
                    track = track,
                    onRemoveAudioTrack = onRemoveAudioTrack
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun SignalMappingRow(
    mapping: SignalTrackMappingUi,
    maxTrack: Int,
    availableTracks: List<UserAudioTrackUi>,
    isPlaying: Boolean,
    onPlaySignal: (Int) -> Unit,
    onSignalTrackSelected: (Int, String) -> Unit,
    onSignalLabelSaved: (Int, String) -> Unit,
    onSignalVolumeChanged: (Int, Int) -> Unit,
    onStopPlayback: () -> Unit,
    onImportTrack: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var isRenameDialogOpen by remember { mutableStateOf(false) }
    val title = mapping.label.ifBlank { "Segnale T${mapping.signalIndex}" }

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
    ) {
        val compact = maxWidth < 700.dp

        if (compact) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SignalTitleBlock(
                    title = title,
                    signalIndex = mapping.signalIndex,
                    trackKey = mapping.trackKey,
                    onRename = { isRenameDialogOpen = true },
                    modifier = Modifier.fillMaxWidth()
                )
                SignalActionRow(
                    mapping = mapping,
                    selectedTrackKey = mapping.trackKey,
                    maxTrack = maxTrack,
                    availableTracks = availableTracks,
                    isPlaying = isPlaying,
                    compact = true,
                    onTrackSelected = { trackId ->
                        onSignalTrackSelected(mapping.signalIndex, trackId)
                    },
                    onVolumeChanged = { volumePercent ->
                        onSignalVolumeChanged(mapping.signalIndex, volumePercent)
                    },
                    onImportTrack = { onImportTrack(mapping.signalIndex) },
                    onPlayOrStop = {
                        if (isPlaying) {
                            onStopPlayback()
                        } else {
                            onPlaySignal(mapping.signalIndex)
                        }
                    }
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                SignalTitleBlock(
                    title = title,
                    signalIndex = mapping.signalIndex,
                    trackKey = mapping.trackKey,
                    onRename = { isRenameDialogOpen = true },
                    modifier = Modifier
                        .weight(0.9f)
                        .fillMaxWidth()
                )
                SignalActionRow(
                    mapping = mapping,
                    selectedTrackKey = mapping.trackKey,
                    maxTrack = maxTrack,
                    availableTracks = availableTracks,
                    isPlaying = isPlaying,
                    compact = false,
                    onTrackSelected = { trackId ->
                        onSignalTrackSelected(mapping.signalIndex, trackId)
                    },
                    onVolumeChanged = { volumePercent ->
                        onSignalVolumeChanged(mapping.signalIndex, volumePercent)
                    },
                    onImportTrack = { onImportTrack(mapping.signalIndex) },
                    onPlayOrStop = {
                        if (isPlaying) {
                            onStopPlayback()
                        } else {
                            onPlaySignal(mapping.signalIndex)
                        }
                    },
                    modifier = Modifier.weight(1.7f)
                )
            }
        }
    }

    if (isRenameDialogOpen) {
        SignalLabelDialog(
            initialLabel = mapping.label,
            signalIndex = mapping.signalIndex,
            onDismiss = { isRenameDialogOpen = false },
            onSave = { label ->
                onSignalLabelSaved(mapping.signalIndex, label)
                isRenameDialogOpen = false
            }
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun SignalTitleBlock(
    title: String,
    signalIndex: Int,
    trackKey: String,
    onRename: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.combinedClickable(
                onClick = {},
                onLongClick = onRename
            )
        )
        Text(
            text = "T$signalIndex -> $trackKey",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SignalActionRow(
    mapping: SignalTrackMappingUi,
    selectedTrackKey: String,
    maxTrack: Int,
    availableTracks: List<UserAudioTrackUi>,
    isPlaying: Boolean,
    compact: Boolean,
    onTrackSelected: (String) -> Unit,
    onVolumeChanged: (Int) -> Unit,
    onImportTrack: () -> Unit,
    onPlayOrStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (compact) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TrackDropdown(
                selectedTrackKey = selectedTrackKey,
                maxTrack = maxTrack,
                availableTracks = availableTracks,
                onTrackSelected = onTrackSelected,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VolumeButton(
                    volumePercent = mapping.volumePercent,
                    signalIndex = mapping.signalIndex,
                    onVolumeChanged = onVolumeChanged,
                    compact = true,
                    modifier = Modifier.weight(1f)
                )

                OutlinedButton(
                    onClick = onImportTrack,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Carica",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = onPlayOrStop,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (isPlaying) "Stop" else "Prova",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TrackDropdown(
                selectedTrackKey = selectedTrackKey,
                maxTrack = maxTrack,
                availableTracks = availableTracks,
                onTrackSelected = onTrackSelected,
                modifier = Modifier.weight(1.2f)
            )

            VolumeButton(
                volumePercent = mapping.volumePercent,
                signalIndex = mapping.signalIndex,
                onVolumeChanged = onVolumeChanged,
                compact = false,
                modifier = Modifier.weight(0.75f)
            )

            OutlinedButton(
                onClick = onImportTrack,
                modifier = Modifier.weight(0.85f)
            ) {
                Text(
                    text = "Carica",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            OutlinedButton(
                onClick = onPlayOrStop,
                modifier = Modifier.weight(0.75f)
            ) {
                Text(
                    text = if (isPlaying) "Stop" else "Prova",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun VolumeButton(
    volumePercent: Int,
    signalIndex: Int,
    onVolumeChanged: (Int) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    var isDialogOpen by remember { mutableStateOf(false) }

    OutlinedButton(
        onClick = { isDialogOpen = true },
        modifier = modifier
    ) {
        Text(
            text = if (compact) {
                "Vol ${volumePercent.coerceIn(10, 100)}%"
            } else {
                "${volumePercent.coerceIn(10, 100)}%"
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }

    if (isDialogOpen) {
        VolumeDialog(
            signalIndex = signalIndex,
            initialVolume = volumePercent,
            onDismiss = { isDialogOpen = false },
            onSave = { volume ->
                onVolumeChanged(volume)
                isDialogOpen = false
            }
        )
    }
}

@Composable
private fun VolumeDialog(
    signalIndex: Int,
    initialVolume: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    var volume by remember { mutableIntStateOf(initialVolume.coerceIn(10, 100)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Volume T$signalIndex") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("${volume}%")
                Slider(
                    value = volume.toFloat(),
                    onValueChange = { volume = it.toInt().coerceIn(10, 100) },
                    valueRange = 10f..100f,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "100% mantiene il volume originale.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(volume) }) {
                Text("Salva")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annulla")
            }
        }
    )
}

@Composable
private fun UserAudioTrackRow(
    track: UserAudioTrackUi,
    onRemoveAudioTrack: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isRemoveDialogOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "${track.fileName} (${track.sourceLabel})",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = if (track.associatedSignals.isEmpty()) {
                        "Non associato"
                    } else {
                        "Associato a ${track.associatedSignals.joinToString { signal -> "T$signal" }}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (track.canRemove) {
                TextButton(onClick = { isRemoveDialogOpen = true }) {
                    Text("Rimuovi")
                }
            }
        }

        Text(
            text = track.relativePath,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (isRemoveDialogOpen) {
        AlertDialog(
            onDismissRequest = { isRemoveDialogOpen = false },
            title = { Text("Rimuovi audio") },
            text = {
                Text(
                    if (track.sourceLabel == "default") {
                        "Rimuovi \"${track.fileName}\" dal progetto. Il file resta nel pacchetto dell'app, ma non comparirà più in questa serie."
                    } else {
                        "Rimuovi \"${track.fileName}\" dai file caricati. Se è associato a un segnale, quel segnale passerà alla prima traccia disponibile."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveAudioTrack(track.trackKey)
                        isRemoveDialogOpen = false
                    }
                ) {
                    Text("Rimuovi")
                }
            },
            dismissButton = {
                TextButton(onClick = { isRemoveDialogOpen = false }) {
                    Text("Annulla")
                }
            }
        )
    }
}

@Composable
private fun SignalLabelDialog(
    initialLabel: String,
    signalIndex: Int,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var label by remember(initialLabel) { mutableStateOf(initialLabel) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nome attivatore T$signalIndex") },
        text = {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                singleLine = true,
                label = { Text("Nome") },
                placeholder = { Text("Nome attivatore") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(label) }) {
                Text("Salva")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annulla")
            }
        }
    )
}

@Composable
private fun TrackDropdown(
    selectedTrackKey: String,
    maxTrack: Int,
    availableTracks: List<UserAudioTrackUi>,
    onTrackSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val tracks = (availableTracks + fallbackTrack(selectedTrackKey, maxTrack))
        .distinctBy { it.trackKey }
        .sortedWith(
            compareBy<UserAudioTrackUi> { it.trackId ?: Int.MAX_VALUE }
                .thenBy { it.fileName }
        )

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = selectedTrackKey,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            tracks.forEach { track ->
                DropdownMenuItem(
                    text = {
                        Text(
                            if (track.inProjectRange) {
                                track.fileName
                            } else {
                                "${track.fileName} fuori range"
                            }
                        )
                    },
                    onClick = {
                        expanded = false
                        if (track.trackKey != selectedTrackKey) {
                            onTrackSelected(track.trackKey)
                        }
                    }
                )
            }
        }
    }
}

private fun fallbackTrack(trackKey: String, maxTrack: Int): UserAudioTrackUi {
    val trackId = trackKey.substringBeforeLast(".").toIntOrNull()
    return UserAudioTrackUi(
        trackKey = trackKey,
        trackId = trackId,
        fileName = trackKey,
        relativePath = trackKey,
        sourceLabel = "mapping",
        inProjectRange = trackId?.let { it in 1..maxTrack } ?: true
    )
}
