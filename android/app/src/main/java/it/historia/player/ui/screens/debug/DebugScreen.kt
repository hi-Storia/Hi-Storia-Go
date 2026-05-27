package it.historia.player.ui.screens.debug

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import it.historia.player.data.usb.UsbConnectionState
import it.historia.player.ui.components.LogPanel
import it.historia.player.ui.screens.connect.ConnectUiState
import it.historia.player.ui.screens.connect.SignalTrackMappingUi
import it.historia.player.ui.screens.connect.UserAudioTrackUi
import kotlinx.coroutines.delay
import java.util.Locale

const val DEBUG_SERIAL = true

@Composable
fun AdminScreen(
    state: ConnectUiState,
    onBackClick: () -> Unit,
    onThresholdChanged: (Int) -> Unit,
    onSendThreshold: () -> Unit,
    onResetArduino: () -> Unit,
    onRestartTouch: () -> Unit,
    onResetBoard: () -> Unit,
    onRestartUsb: () -> Unit,
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
    onImportPresentationTrack: () -> Unit,
    onRemovePresentationTrack: () -> Unit,
    onImportBackgroundMusic: () -> Unit,
    onIdleTimeoutChanged: (String) -> Unit,
    onIdleUseDefaultTrackChanged: (Boolean) -> Unit,
    onExplorationUseDefaultTrackChanged: (Boolean) -> Unit,
    onBackgroundMusicEnabledChanged: (Boolean) -> Unit,
    onBackgroundMusicUseDefaultChanged: (Boolean) -> Unit,
    onBackgroundMusicOnlyDuringTracksChanged: (Boolean) -> Unit,
    onBackgroundMusicFullVolumeChanged: (Float) -> Unit,
    onBackgroundMusicDuckedVolumeChanged: (Float) -> Unit,
    onExplorationTimeoutChanged: (String) -> Unit,
    onProjectMessageDismissed: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Board", "Contenuti", "Smartphone")

    @Composable
    fun SelectedSection(sectionModifier: Modifier = Modifier) {
        when (selectedTab) {
            0 -> DeviceAdminSection(
                state = state,
                onThresholdChanged = onThresholdChanged,
                onSendThreshold = onSendThreshold,
                onResetArduino = onResetArduino,
                onRestartTouch = onRestartTouch,
                onResetBoard = onResetBoard,
                onRestartUsb = onRestartUsb,
                modifier = sectionModifier
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
                onImportPresentationTrack = onImportPresentationTrack,
                onRemovePresentationTrack = onRemovePresentationTrack,
                onImportBackgroundMusic = onImportBackgroundMusic,
                onIdleUseDefaultTrackChanged = onIdleUseDefaultTrackChanged,
                onExplorationUseDefaultTrackChanged = onExplorationUseDefaultTrackChanged,
                onBackgroundMusicEnabledChanged = onBackgroundMusicEnabledChanged,
                onBackgroundMusicUseDefaultChanged = onBackgroundMusicUseDefaultChanged,
                onBackgroundMusicOnlyDuringTracksChanged = onBackgroundMusicOnlyDuringTracksChanged,
                modifier = sectionModifier
            )

            2 -> IdleAdminSection(
                state = state,
                onIdleTimeoutChanged = onIdleTimeoutChanged,
                onBackgroundMusicFullVolumeChanged = onBackgroundMusicFullVolumeChanged,
                onBackgroundMusicDuckedVolumeChanged = onBackgroundMusicDuckedVolumeChanged,
                onExplorationTimeoutChanged = onExplorationTimeoutChanged,
                modifier = sectionModifier
            )
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        val landscape = maxWidth > maxHeight

        if (landscape) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(176.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Admin",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Button(
                        onClick = onBackClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Back")
                    }
                    HorizontalDivider()
                    tabs.forEachIndexed { index, label ->
                        if (selectedTab == index) {
                            Button(
                                onClick = { selectedTab = index },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(label)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { selectedTab = index },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(label)
                            }
                        }
                    }
                }

                SelectedSection(
                    sectionModifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
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

                SelectedSection(
                    sectionModifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
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
    onBackgroundMusicFullVolumeChanged: (Float) -> Unit,
    onBackgroundMusicDuckedVolumeChanged: (Float) -> Unit,
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
            text = "Volume massimo: ${formatPercent(state.backgroundMusicSettings.fullVolumePercent)}",
            style = MaterialTheme.typography.bodyMedium
        )
        Slider(
            value = state.backgroundMusicSettings.fullVolumePercent,
            onValueChange = { onBackgroundMusicFullVolumeChanged(snapVolumePercent(it)) },
            valueRange = 0f..100f,
            enabled = state.backgroundMusicSettings.enabled &&
                !state.backgroundMusicSettings.onlyDuringTracks,
            modifier = Modifier.fillMaxWidth()
        )
        LowVolumeButtons(
            enabled = state.backgroundMusicSettings.enabled &&
                !state.backgroundMusicSettings.onlyDuringTracks,
            onValueSelected = onBackgroundMusicFullVolumeChanged
        )

        Text(
            text = "Volume sotto traccia: ${formatPercent(state.backgroundMusicSettings.duckedVolumePercent)}",
            style = MaterialTheme.typography.bodyMedium
        )
        Slider(
            value = state.backgroundMusicSettings.duckedVolumePercent,
            onValueChange = { onBackgroundMusicDuckedVolumeChanged(snapVolumePercent(it)) },
            valueRange = 0f..100f,
            enabled = state.backgroundMusicSettings.enabled,
            modifier = Modifier.fillMaxWidth()
        )
        LowVolumeButtons(
            enabled = state.backgroundMusicSettings.enabled,
            onValueSelected = onBackgroundMusicDuckedVolumeChanged
        )

        HorizontalDivider()

        AppDiagnosticsSection(state = state)
    }
}

@Composable
private fun LowVolumeButtons(
    enabled: Boolean,
    onValueSelected: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf(0f, 0.2f, 0.4f, 0.6f, 0.8f, 1f).forEach { value ->
            OutlinedButton(
                onClick = { onValueSelected(value) },
                enabled = enabled,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = formatLowVolumeButtonLabel(value),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DeviceAdminSection(
    state: ConnectUiState,
    onThresholdChanged: (Int) -> Unit,
    onSendThreshold: () -> Unit,
    onResetArduino: () -> Unit,
    onRestartTouch: () -> Unit,
    onResetBoard: () -> Unit,
    onRestartUsb: () -> Unit,
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onResetArduino,
                modifier = Modifier.weight(1f)
            ) {
                Text("Invia fine traccia")
            }

            OutlinedButton(
                onClick = onRestartUsb,
                modifier = Modifier.weight(1f)
            ) {
                Text("Riavvia porta USB")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onRestartTouch,
                modifier = Modifier.weight(1f)
            ) {
                Text("Riavvia touch")
            }

            OutlinedButton(
                onClick = onResetBoard,
                modifier = Modifier.weight(1f)
            ) {
                Text("Reset Arduino")
            }
        }

        if (DEBUG_SERIAL) {
            LogPanel(
                lines = state.serialLogs,
                modifier = Modifier.fillMaxWidth()
            )

            SerialDiagnosticsSection(state = state)
        }
    }
}

@Composable
private fun SerialDiagnosticsSection(state: ConnectUiState) {
    val diagnostics = state.diagnostics
    val nowMs = rememberElapsedRealtimeTicker()
    val lastLineAgeMs = diagnostics.lastSerialLineAtMs?.let { nowMs - it }
    val serialStale = state.usbConnectionState == UsbConnectionState.CONNECTED &&
        (lastLineAgeMs == null || lastLineAgeMs > SERIAL_STALE_WARNING_MS)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Diagnostica seriale",
            style = MaterialTheme.typography.titleSmall
        )
        DebugLine("Stato USB", state.usbConnectionState.toString())
        DebugLine(
            "Ultimo cambio USB",
            formatAgo(nowMs, diagnostics.lastUsbStateChangedAtMs)
        )
        DebugLine(
            "Dati Arduino",
            formatAgo(nowMs, diagnostics.lastSerialLineAtMs),
            isWarning = serialStale
        )
        DebugLine(
            "Byte USB",
            diagnostics.serialByteCount.toString()
        )
        DebugLine(
            "Ultimo byte",
            formatAgo(nowMs, diagnostics.lastSerialByteAtMs),
            isWarning = state.usbConnectionState == UsbConnectionState.CONNECTED &&
                diagnostics.lastSerialByteAtMs != null &&
                nowMs - diagnostics.lastSerialByteAtMs > SERIAL_STALE_WARNING_MS
        )
        DebugLine(
            "Timeout lettura",
            formatAgo(nowMs, diagnostics.lastSerialReadTimeoutAtMs)
        )
        DebugLine(
            "Riga parziale",
            "${diagnostics.serialPartialLineLength} char"
        )
        if (diagnostics.lastSerialLine.isNotBlank()) {
            DebugLine("Valore", diagnostics.lastSerialLine)
        }
        DebugLine(
            "Ultimo evento T",
            diagnostics.lastTouchSignal?.let {
                "T$it, ${formatAgo(nowMs, diagnostics.lastTouchAtMs)}"
            } ?: "mai"
        )
        DebugLine(
            "Ultimo TX",
            diagnostics.lastArduinoTx.takeIf { it.isNotBlank() }?.let {
                "$it, ${formatAgo(nowMs, diagnostics.lastArduinoTxAtMs)}"
            } ?: "mai"
        )
        if (serialStale) {
            Text(
                text = "USB connessa, ma non arrivano righe complete da Arduino.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            if (diagnostics.lastSerialByteAtMs != null &&
                nowMs - diagnostics.lastSerialByteAtMs <= SERIAL_STALE_WARNING_MS
            ) {
                Text(
                    text = "Arrivano byte ma non righe: possibile newline mancante o buffer seriale sporco.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        } else if (state.usbConnectionState == UsbConnectionState.CONNECTED &&
            diagnostics.lastSerialLineAtMs != null &&
            diagnostics.lastTouchAtMs == null
        ) {
            Text(
                text = "La seriale riceve dati, ma non sono ancora arrivati eventi T.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        CriticalEventsSection(state = state, nowMs = nowMs)
    }
}

@Composable
private fun CriticalEventsSection(
    state: ConnectUiState,
    nowMs: Long
) {
    val diagnostics = state.diagnostics

    HorizontalDivider()
    Text(
        text = "Eventi critici",
        style = MaterialTheme.typography.titleSmall
    )
    DebugLine(
        "Ultimo",
        diagnostics.lastCriticalEvent.takeIf { it.isNotBlank() }?.let {
            "$it, ${formatAgo(nowMs, diagnostics.lastCriticalEventAtMs)}"
        } ?: "nessuno"
    )
    DebugLine(
        "Boot Arduino",
        "${diagnostics.bootCount} - ${formatAgo(nowMs, diagnostics.lastBootAtMs)}"
    )
    DebugLine(
        "Ready",
        "${diagnostics.readyCount} - ${formatAgo(nowMs, diagnostics.lastReadyAtMs)}"
    )
    DebugLine(
        "Freeze touch",
        "${diagnostics.touchFreezeCount} - ${formatAgo(nowMs, diagnostics.lastTouchFreezeAtMs)}",
        isWarning = diagnostics.touchFreezeCount > 0
    )
    DebugLine(
        "Restart touch",
        "${diagnostics.touchRestartCount} - ${formatAgo(nowMs, diagnostics.lastTouchRestartAtMs)}"
    )
    DebugLine(
        "Reset Arduino",
        "${diagnostics.boardResetCount} - ${formatAgo(nowMs, diagnostics.lastBoardResetAtMs)}",
        isWarning = diagnostics.boardResetCount > 0
    )
    DebugLine(
        "Errori USB",
        "${diagnostics.usbErrorCount} - ${formatAgo(nowMs, diagnostics.lastUsbErrorAtMs)}",
        isWarning = diagnostics.usbErrorCount > 0
    )
}

@Composable
private fun AppDiagnosticsSection(state: ConnectUiState) {
    val diagnostics = state.diagnostics
    val nowMs = rememberElapsedRealtimeTicker()
    val foreground = when {
        state.isExplorationMode -> "Esplorazione"
        state.isPresentationPlaying -> "Presentazione"
        state.isIdlePlaying -> "Traccia 0"
        state.nowPlayingSignal != null -> "T${state.nowPlayingSignal}"
        else -> "Nessun audio"
    }
    val background = when {
        !state.backgroundMusicSettings.enabled -> "OFF"
        state.backgroundMusicSettings.onlyDuringTracks -> "solo sotto tracce"
        else -> "continua"
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Diagnostica app",
            style = MaterialTheme.typography.titleSmall
        )
        DebugLine("App attiva", formatDuration(nowMs - diagnostics.appStartedAtMs))
        DebugLine("Percorso", state.contextLabel)
        DebugLine("Audio", foreground)
        DebugLine("Avanzamento", "${(state.playbackProgress * 100f).toInt().coerceIn(0, 100)}%")
        DebugLine("Musica", background)
        DebugLine(
            "Volumi musica",
            "${formatPercent(state.backgroundMusicSettings.fullVolumePercent)} / " +
                formatPercent(state.backgroundMusicSettings.duckedVolumePercent)
        )
        DebugLine("Touch ricevuti", diagnostics.touchEventCount.toString())
        DebugLine(
            "Ultimo touch",
            diagnostics.lastTouchSignal?.let {
                "T$it, ${formatAgo(nowMs, diagnostics.lastTouchAtMs)}"
            } ?: "mai"
        )
        PhoneDiagnosticsSection()
    }
}

@Composable
private fun PhoneDiagnosticsSection() {
    val context = LocalContext.current
    var stats by remember { mutableStateOf(readPhoneStats(context)) }

    LaunchedEffect(context) {
        while (true) {
            stats = readPhoneStats(context)
            delay(5_000L)
        }
    }

    DebugLine("RAM libera", "${formatBytes(stats.availableRamBytes)} / ${formatBytes(stats.totalRamBytes)}")
    DebugLine("Heap app", "${formatBytes(stats.usedHeapBytes)} / ${formatBytes(stats.maxHeapBytes)}")
    DebugLine("Spazio libero", "${formatBytes(stats.availableStorageBytes)} / ${formatBytes(stats.totalStorageBytes)}")
    DebugLine("CPU", "non disponibile in modo affidabile")
}

@Composable
private fun rememberElapsedRealtimeTicker(): Long {
    var nowMs by remember { mutableStateOf(SystemClock.elapsedRealtime()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L)
            nowMs = SystemClock.elapsedRealtime()
        }
    }

    return nowMs
}

@Composable
private fun DebugLine(
    label: String,
    value: String,
    isWarning: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.38f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            modifier = Modifier.weight(0.62f),
            style = MaterialTheme.typography.bodySmall,
            color = if (isWarning) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
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
    onImportPresentationTrack: () -> Unit,
    onRemovePresentationTrack: () -> Unit,
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
            text = "Presentazione",
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            text = state.presentationSettings.customTrackKey ?: "Nessuna traccia di presentazione",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onImportPresentationTrack,
                modifier = Modifier.weight(1f)
            ) {
                Text("Carica presentazione")
            }

            OutlinedButton(
                onClick = onRemovePresentationTrack,
                enabled = state.presentationSettings.customTrackKey != null,
                modifier = Modifier.weight(1f)
            ) {
                Text("Rimuovi")
            }
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

private const val SERIAL_STALE_WARNING_MS = 5_000L

private data class PhoneStats(
    val availableRamBytes: Long = 0L,
    val totalRamBytes: Long = 0L,
    val usedHeapBytes: Long = 0L,
    val maxHeapBytes: Long = 0L,
    val availableStorageBytes: Long = 0L,
    val totalStorageBytes: Long = 0L
)

private fun snapVolumePercent(value: Float): Float {
    val clamped = value.coerceIn(0f, 100f)
    return if (clamped < 1f) {
        (kotlin.math.round(clamped * 5f) / 5f).coerceIn(0f, 1f)
    } else {
        kotlin.math.round(clamped)
    }
}

private fun formatPercent(value: Float): String {
    val snapped = snapVolumePercent(value)
    return if (snapped < 1f && snapped > 0f) {
        "${String.format(Locale.US, "%.1f", snapped)}%"
    } else {
        "${snapped.toInt()}%"
    }
}

private fun formatLowVolumeButtonLabel(value: Float): String {
    val snapped = snapVolumePercent(value)
    return when {
        snapped == 0f -> "0%"
        snapped < 1f -> ".${(snapped * 10f).toInt()}%"
        else -> "${snapped.toInt()}%"
    }
}

private fun readPhoneStats(context: Context): PhoneStats {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val memoryInfo = ActivityManager.MemoryInfo()
    activityManager?.getMemoryInfo(memoryInfo)

    val runtime = Runtime.getRuntime()
    val usedHeap = runtime.totalMemory() - runtime.freeMemory()
    val statFs = StatFs(Environment.getDataDirectory().absolutePath)

    return PhoneStats(
        availableRamBytes = memoryInfo.availMem,
        totalRamBytes = memoryInfo.totalMem,
        usedHeapBytes = usedHeap,
        maxHeapBytes = runtime.maxMemory(),
        availableStorageBytes = statFs.availableBytes,
        totalStorageBytes = statFs.totalBytes
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) {
        return "n/d"
    }
    val gib = bytes / (1024.0 * 1024.0 * 1024.0)
    return if (gib >= 1.0) {
        "${String.format(Locale.US, "%.1f", gib)} GB"
    } else {
        "${bytes / (1024L * 1024L)} MB"
    }
}

private fun formatAgo(nowMs: Long, thenMs: Long?): String {
    if (thenMs == null || thenMs <= 0L) {
        return "mai"
    }
    val elapsed = (nowMs - thenMs).coerceAtLeast(0L)
    return when {
        elapsed < 1_500L -> "adesso"
        elapsed < 60_000L -> "${elapsed / 1_000L} s fa"
        elapsed < 3_600_000L -> "${elapsed / 60_000L} min fa"
        else -> "${elapsed / 3_600_000L} h fa"
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs.coerceAtLeast(0L) / 1_000L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "${hours}h ${minutes.toString().padStart(2, '0')}m"
    } else {
        "${minutes}m ${seconds.toString().padStart(2, '0')}s"
    }
}
