package it.historia.player.ui.screens.connect

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.historia.player.R
import it.historia.player.data.usb.UsbConnectionState
import it.historia.player.domain.player.DeviceCatalog
import kotlinx.coroutines.withTimeoutOrNull
import android.os.SystemClock

private val PlayerBackground = Color(0xFF1E6078)
private val ExplorationBackground = Color(0xFF6B8EA3)
private val Highlight = Color(0xFFF2D36B)
private val SoftText = Color(0xFFD8E7EA)
private const val ADMIN_TAP_COUNT = 5
private const val ADMIN_TAP_WINDOW_MS = 2_200L

@Composable
fun ConnectScreen(
    state: ConnectUiState,
    onStopClick: () -> Unit,
    onStartExplorationClick: () -> Unit,
    onStopExplorationClick: () -> Unit,
    onProfileSelected: (String) -> Unit,
    onLanguageSelected: (String) -> Unit,
    onOpenDebug: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.isExplorationMode) {
        ExplorationModeScreen(
            state = state,
            onStopExplorationClick = onStopExplorationClick,
            modifier = modifier
        )
        return
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(PlayerBackground)
            .safeDrawingPadding()
            .padding(14.dp)
    ) {
        val isLandscape = maxWidth > maxHeight
        val hasRoomyPortrait = maxHeight > 660.dp
        if (isLandscape) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LogoPanel(
                    onOpenDebug = onOpenDebug,
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.62f),
                    compact = false
                )
                HomeControls(
                    state = state,
                    onStopClick = onStopClick,
                    onStartExplorationClick = onStartExplorationClick,
                    onProfileSelected = onProfileSelected,
                    onLanguageSelected = onLanguageSelected,
                    roomy = true,
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1.38f)
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LogoPanel(
                    onOpenDebug = onOpenDebug,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.36f),
                    compact = true
                )
                HomeControls(
                    state = state,
                    onStopClick = onStopClick,
                    onStartExplorationClick = onStartExplorationClick,
                    onProfileSelected = onProfileSelected,
                    onLanguageSelected = onLanguageSelected,
                    roomy = hasRoomyPortrait,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.64f)
                )
            }
        }
    }
}

@Composable
private fun LogoPanel(
    compact: Boolean,
    onOpenDebug: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var tapCount by remember { mutableStateOf(0) }
    var firstTapAt by remember { mutableStateOf(0L) }
    val imageModifier = Modifier
        .fillMaxWidth(if (compact) 0.58f else 0.84f)
        .sizeIn(maxWidth = if (compact) 220.dp else 300.dp, maxHeight = if (compact) 220.dp else 300.dp)
        .aspectRatio(1f)
        .let { baseModifier ->
            if (onOpenDebug == null) {
                baseModifier
            } else {
                baseModifier.clickable {
                    val now = SystemClock.elapsedRealtime()
                    if (firstTapAt == 0L || now - firstTapAt > ADMIN_TAP_WINDOW_MS) {
                        firstTapAt = now
                        tapCount = 1
                    } else {
                        tapCount += 1
                    }

                    if (tapCount >= ADMIN_TAP_COUNT) {
                        tapCount = 0
                        firstTapAt = 0L
                        onOpenDebug()
                    }
                }
            }
        }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo_hi_storia),
            contentDescription = "hi-storia",
            modifier = imageModifier
        )
    }
}

@Composable
private fun HomeControls(
    state: ConnectUiState,
    onStopClick: () -> Unit,
    onStartExplorationClick: () -> Unit,
    onProfileSelected: (String) -> Unit,
    onLanguageSelected: (String) -> Unit,
    roomy: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProjectHeader(
                state = state,
                roomy = roomy,
                modifier = Modifier.fillMaxWidth()
            )

            StatusLine(state = state)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SelectionDropdown(
                    label = "Tipologia",
                    selectedValue = state.selectedContext.profile,
                    selectedLabel = profileLabel(state, state.selectedContext.profile),
                    options = state.availableProfiles,
                    optionLabel = { profile -> profileLabel(state, profile) },
                    onOptionSelected = onProfileSelected,
                    modifier = Modifier.weight(1f)
                )

                SelectionDropdown(
                    label = "Lingua",
                    selectedValue = state.selectedContext.language,
                    selectedLabel = languageLabel(state, state.selectedContext.language),
                    options = state.availableLanguages,
                    optionLabel = { language -> languageLabel(state, language) },
                    onOptionSelected = onLanguageSelected,
                    modifier = Modifier.weight(1f)
                )
            }

            NowPlayingPanel(
                state = state,
                onStopClick = onStopClick
            )

            Button(
                onClick = onStartExplorationClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Highlight,
                    contentColor = PlayerBackground
                )
            ) {
                Text(
                    text = "Modalita esplorazione",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ProjectHeader(
    state: ConnectUiState,
    roomy: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomStart
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(if (roomy) 6.dp else 3.dp)
        ) {
        Text(
            text = state.selectedDeviceDisplayName,
            style = if (roomy) {
                MaterialTheme.typography.headlineLarge.copy(lineHeight = 38.sp)
            } else {
                MaterialTheme.typography.headlineMedium.copy(lineHeight = 31.sp)
            },
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = if (roomy) 3 else 2,
            overflow = TextOverflow.Ellipsis
        )
        if (state.selectedDeviceCredit.isNotBlank()) {
            Text(
                text = state.selectedDeviceCredit,
                style = if (roomy) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                color = Highlight,
                fontWeight = FontWeight.Medium,
                maxLines = if (roomy) 2 else 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        }
    }
}

@Composable
private fun StatusLine(state: ConnectUiState) {
    val connected = state.usbConnectionState == UsbConnectionState.CONNECTED
    val statusText = when {
        connected -> "Scheda collegata"
        state.usbConnectionState == UsbConnectionState.WAITING_PERMISSION -> "Autorizza USB"
        else -> "Scheda non collegata"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = if (connected) Color(0xFF2FDF9B) else Color(0xFFFFB08D),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .width(10.dp)
                .height(10.dp)
        ) {}
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall,
            color = SoftText
        )
    }
}

@Composable
private fun NowPlayingPanel(
    state: ConnectUiState,
    onStopClick: () -> Unit
) {
    val hasAudio = state.nowPlayingSignal != null || state.isIdlePlaying
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.Black.copy(alpha = 0.18f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "In ascolto",
                    style = MaterialTheme.typography.bodySmall,
                    color = Highlight
                )
                Text(
                    text = formatNowPlaying(state),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (hasAudio) {
                OutlinedButton(
                    onClick = onStopClick,
                    modifier = Modifier.height(36.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    )
                ) {
                    Text("Stop")
                }
            }
        }
    }
}

@Composable
private fun ExplorationModeScreen(
    state: ConnectUiState,
    onStopExplorationClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(ExplorationBackground)
            .safeDrawingPadding()
            .padding(14.dp)
            .closeOnThreeSecondPress(onStopExplorationClick),
        contentAlignment = Alignment.Center
    ) {
        val isLandscape = maxWidth > maxHeight
        if (isLandscape) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LogoPanel(
                    compact = false,
                    modifier = Modifier
                        .weight(0.62f)
                        .fillMaxHeight()
                )
                ExplorationControls(
                    state = state,
                    onStopExplorationClick = onStopExplorationClick,
                    modifier = Modifier
                        .weight(1.38f)
                        .fillMaxHeight()
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LogoPanel(
                    compact = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.36f)
                )
                ExplorationControls(
                    state = state,
                    onStopExplorationClick = onStopExplorationClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.64f)
                )
            }
        }
    }
}

@Composable
private fun ExplorationControls(
    state: ConnectUiState,
    onStopExplorationClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Modalita esplorazione",
                style = MaterialTheme.typography.headlineMedium.copy(lineHeight = 31.sp),
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Text(
                text = "Le tracce sono mute. Gli attivatori emettono un segnale acustico.",
                style = MaterialTheme.typography.bodyMedium,
                color = SoftText
            )
            Text(
                text = "Chiusura automatica tra ${state.explorationSettings.timeoutMinutes} min",
                style = MaterialTheme.typography.bodySmall,
                color = Highlight
            )
            Button(
                onClick = onStopExplorationClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Highlight,
                    contentColor = PlayerBackground
                )
            ) {
                Text("Chiudi")
            }
            Text(
                text = "Pressione lunga 3 secondi per chiudere",
                style = MaterialTheme.typography.bodySmall,
                color = SoftText
            )
        }
    }
}

private fun Modifier.closeOnThreeSecondPress(onClose: () -> Unit): Modifier {
    return pointerInput(onClose) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            val releasedBeforeTimeout = withTimeoutOrNull(3_000L) {
                waitForUpOrCancellation()
                true
            }
            if (releasedBeforeTimeout == null) {
                onClose()
                waitForUpOrCancellation()
            }
        }
    }
}

@Composable
private fun SelectionDropdown(
    label: String,
    selectedValue: String,
    selectedLabel: String,
    options: List<String>,
    optionLabel: (String) -> String,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { if (enabled) expanded = true },
            enabled = enabled,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color.White,
                disabledContentColor = SoftText.copy(alpha = 0.55f)
            )
        ) {
            Text(
                text = "$label: $selectedLabel",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        expanded = false
                        if (option != selectedValue) {
                            onOptionSelected(option)
                        }
                    }
                )
            }
        }
    }
}

private fun profileLabel(state: ConnectUiState, profile: String): String {
    return state.projectSettings.profileLabels[profile]
        ?.takeIf { it.isNotBlank() }
        ?: when (profile) {
            DeviceCatalog.PROFILE_ADULT -> "Adulti"
            else -> profile
        }
}

private fun languageLabel(state: ConnectUiState, language: String): String {
    return state.projectSettings.languageLabels[language]
        ?.takeIf { it.isNotBlank() }
        ?: when (language) {
            DeviceCatalog.LANGUAGE_IT -> "Italiano"
            DeviceCatalog.LANGUAGE_EN -> "Inglese"
            DeviceCatalog.LANGUAGE_ES -> "Spagnolo"
            else -> language.uppercase()
        }
}

private fun formatNowPlaying(state: ConnectUiState): String {
    if (state.isIdlePlaying) {
        return "Traccia 0 / idle"
    }

    val signal = state.nowPlayingSignal ?: return "In attesa del tocco"
    val label = state.signalTrackMappings
        .firstOrNull { it.signalIndex == signal }
        ?.label
        .orEmpty()
    if (label.isNotBlank()) {
        return label
    }

    val mapping = state.signalTrackMappings
        .firstOrNull { it.signalIndex == signal }
        ?.trackKey
        ?: return "Attivatore T$signal"
    return "T$signal -> $mapping"
}
