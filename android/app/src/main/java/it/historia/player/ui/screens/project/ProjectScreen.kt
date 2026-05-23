package it.historia.player.ui.screens.project

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import it.historia.player.ui.screens.connect.ConnectUiState
import it.historia.player.ui.screens.connect.ProjectSeriesUi

@Composable
fun ProjectScreen(
    state: ConnectUiState,
    onBackClick: () -> Unit,
    onProjectNameChanged: (String) -> Unit,
    onProjectSchoolChanged: (String) -> Unit,
    onProjectTrackCountChanged: (String) -> Unit,
    onProfileLabelChanged: (String, String) -> Unit,
    onLanguageLabelChanged: (String, String) -> Unit,
    onSeriesEnabledChanged: (String, String, Boolean) -> Unit,
    onProfileEnabledChanged: (String, Boolean) -> Unit,
    onLanguageAdded: (String, String) -> Unit,
    onProfileAdded: (String, String) -> Unit,
    onProfileRemoved: (String) -> Unit,
    onLanguageRemoved: (String, String) -> Unit,
    onProjectReset: () -> Unit,
    onProjectMessageDismissed: () -> Unit,
    modifier: Modifier = Modifier
) {
    var languageDialogProfileId by remember { mutableStateOf<String?>(null) }
    var isProfileDialogOpen by remember { mutableStateOf(false) }
    var isResetDialogOpen by remember { mutableStateOf(false) }
    var profileToRemove by remember { mutableStateOf<Pair<String, String>?>(null) }
    var languageToRemove by remember { mutableStateOf<Triple<String, String, String>?>(null) }

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
                text = "Progetto",
                style = MaterialTheme.typography.titleLarge
            )
            Button(onClick = onBackClick) {
                Text("Back")
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = state.projectSettings.culturalAssetName,
                onValueChange = onProjectNameChanged,
                label = { Text("Nome bene culturale") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.projectSettings.schoolName,
                onValueChange = onProjectSchoolChanged,
                label = { Text("Nome scuola") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.projectSettings.trackCount.toString(),
                onValueChange = onProjectTrackCountChanged,
                label = { Text("Numero attivatori / tracce") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider()

            Text(
                text = "Tipologie e lingue",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "Disattivare una tipologia o una lingua rimuove quella serie dal player, ma non cancella tracce o associazioni.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            state.projectSettings.series
                .groupBy { it.profileId }
                .forEach { (profileId, seriesForProfile) ->
                    val profileLabel = state.projectSettings.profileLabels[profileId].orEmpty()
                    ProfileSeriesEditor(
                        profileId = profileId,
                        profileLabel = profileLabel,
                        series = seriesForProfile,
                        languageLabels = state.projectSettings.languageLabels,
                        canRemoveProfile = state.projectSettings.removableProfiles.contains(profileId),
                        onProfileLabelChanged = onProfileLabelChanged,
                        onLanguageLabelChanged = onLanguageLabelChanged,
                        onSeriesEnabledChanged = onSeriesEnabledChanged,
                        onProfileEnabledChanged = onProfileEnabledChanged,
                        onAddLanguageClick = { languageDialogProfileId = profileId },
                        onRemoveProfileClick = {
                            profileToRemove = profileId to profileLabel.ifBlank { profileId }
                        },
                        onRemoveLanguageClick = { language ->
                            val languageLabel = state.projectSettings.languageLabels[language].orEmpty()
                            languageToRemove = Triple(
                                profileId,
                                language,
                                languageLabel.ifBlank { language.uppercase() }
                            )
                        }
                    )
                }

            Button(
                onClick = { isProfileDialogOpen = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Aggiungi tipologia")
            }

            Button(
                onClick = { isResetDialogOpen = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Reset progetto")
            }
        }
    }

    languageDialogProfileId?.let { profileId ->
        AddLanguageDialog(
            onDismiss = { languageDialogProfileId = null },
            onSave = { languageLabel ->
                onLanguageAdded(profileId, languageLabel)
                languageDialogProfileId = null
            }
        )
    }

    if (isProfileDialogOpen) {
        AddProfileDialog(
            onDismiss = { isProfileDialogOpen = false },
            onSave = { profileLabel, languageLabel ->
                onProfileAdded(profileLabel, languageLabel)
                isProfileDialogOpen = false
            }
        )
    }

    if (isResetDialogOpen) {
        AlertDialog(
            onDismissRequest = { isResetDialogOpen = false },
            title = { Text("Reset progetto") },
            text = {
                Text("Ripristina nomi, tipologie e lingue predefinite. Tracce, associazioni e nomi attivatori non vengono cancellati.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onProjectReset()
                        isResetDialogOpen = false
                    }
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { isResetDialogOpen = false }) {
                    Text("Annulla")
                }
            }
        )
    }

    profileToRemove?.let { (profileId, profileLabel) ->
        AlertDialog(
            onDismissRequest = { profileToRemove = null },
            title = { Text("Rimuovi tipologia") },
            text = {
                Text("Rimuovi \"$profileLabel\" dal progetto. Audio e associazioni non vengono cancellati.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onProfileRemoved(profileId)
                        profileToRemove = null
                    }
                ) {
                    Text("Rimuovi")
                }
            },
            dismissButton = {
                TextButton(onClick = { profileToRemove = null }) {
                    Text("Annulla")
                }
            }
        )
    }

    languageToRemove?.let { (profileId, language, languageLabel) ->
        AlertDialog(
            onDismissRequest = { languageToRemove = null },
            title = { Text("Rimuovi lingua") },
            text = {
                Text("Rimuovi \"$languageLabel\" da questa tipologia. Audio e associazioni non vengono cancellati.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onLanguageRemoved(profileId, language)
                        languageToRemove = null
                    }
                ) {
                    Text("Rimuovi")
                }
            },
            dismissButton = {
                TextButton(onClick = { languageToRemove = null }) {
                    Text("Annulla")
                }
            }
        )
    }

    state.projectMessage?.let { message ->
        AlertDialog(
            onDismissRequest = onProjectMessageDismissed,
            title = { Text("Attenzione") },
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
private fun ProfileSeriesEditor(
    profileId: String,
    profileLabel: String,
    series: List<ProjectSeriesUi>,
    languageLabels: Map<String, String>,
    canRemoveProfile: Boolean,
    onProfileLabelChanged: (String, String) -> Unit,
    onLanguageLabelChanged: (String, String) -> Unit,
    onSeriesEnabledChanged: (String, String, Boolean) -> Unit,
    onProfileEnabledChanged: (String, Boolean) -> Unit,
    onAddLanguageClick: () -> Unit,
    onRemoveProfileClick: () -> Unit,
    onRemoveLanguageClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val profileEnabled = series.any { it.enabled }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(
                checked = profileEnabled,
                onCheckedChange = { onProfileEnabledChanged(profileId, it) }
            )

            OutlinedTextField(
                value = profileLabel,
                onValueChange = { onProfileLabelChanged(profileId, it) },
                label = { Text("Nome tipologia") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        if (canRemoveProfile) {
            TextButton(
                onClick = onRemoveProfileClick,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Rimuovi tipologia")
            }
        }

        series.forEach { seriesItem ->
            SeriesLanguageRow(
                series = seriesItem,
                languageLabel = languageLabels[seriesItem.language].orEmpty(),
                onLanguageLabelChanged = onLanguageLabelChanged,
                onSeriesEnabledChanged = onSeriesEnabledChanged,
                onRemoveLanguageClick = onRemoveLanguageClick
            )
        }

        TextButton(onClick = onAddLanguageClick) {
            Text("Aggiungi lingua")
        }

        HorizontalDivider()
    }
}

@Composable
private fun SeriesLanguageRow(
    series: ProjectSeriesUi,
    languageLabel: String,
    onLanguageLabelChanged: (String, String) -> Unit,
    onSeriesEnabledChanged: (String, String, Boolean) -> Unit,
    onRemoveLanguageClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(
                checked = series.enabled,
                onCheckedChange = {
                    onSeriesEnabledChanged(series.profileId, series.language, it)
                }
            )

            OutlinedTextField(
                value = languageLabel,
                onValueChange = { onLanguageLabelChanged(series.language, it) },
                label = { Text(series.language.uppercase()) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        if (series.canRemove) {
            TextButton(
                onClick = { onRemoveLanguageClick(series.language) },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Rimuovi")
            }
        }
    }
}

@Composable
private fun AddLanguageDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var languageLabel by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aggiungi lingua") },
        text = {
            OutlinedTextField(
                value = languageLabel,
                onValueChange = { languageLabel = it },
                label = { Text("Nome lingua") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(languageLabel) },
                enabled = languageLabel.isNotBlank()
            ) {
                Text("Aggiungi")
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
private fun AddProfileDialog(
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var profileLabel by remember { mutableStateOf("") }
    var languageLabel by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aggiungi tipologia") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = profileLabel,
                    onValueChange = { profileLabel = it },
                    label = { Text("Nome tipologia") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = languageLabel,
                    onValueChange = { languageLabel = it },
                    label = { Text("Prima lingua") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(profileLabel, languageLabel) },
                enabled = profileLabel.isNotBlank() && languageLabel.isNotBlank()
            ) {
                Text("Aggiungi")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annulla")
            }
        }
    )
}
