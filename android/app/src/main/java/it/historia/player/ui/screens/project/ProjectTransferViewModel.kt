package it.historia.player.ui.screens.project

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import it.historia.player.data.project.PreparedProjectImport
import it.historia.player.data.project.ProjectArchiveException
import it.historia.player.data.project.ProjectArchiveReader
import it.historia.player.data.project.ProjectDocumentAccess
import it.historia.player.data.project.ProjectImportSummary
import it.historia.player.data.project.ProjectTransferManager
import it.historia.player.domain.player.DeviceCatalog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException

/** Chi gestisce il player: deve fermarsi prima della sostituzione e ricaricarsi dopo. */
interface ProjectReplacementHost {
    val currentProjectDeviceId: String
    fun prepareForProjectReplacement()
    fun onProjectReplacementFinished()
}

data class ProjectImportPreviewUi(
    val projectName: String,
    val schoolName: String,
    val details: List<String>,
    val profiles: List<String>,
    val warnings: List<String>
)

data class ProjectTransferMessage(
    val title: String,
    val text: String,
    val isError: Boolean
)

data class ProjectTransferUiState(
    val busyMessage: String? = null,
    val importPreview: ProjectImportPreviewUi? = null,
    val message: ProjectTransferMessage? = null
) {
    val isBusy: Boolean get() = busyMessage != null
}

/**
 * Stato e flusso di esportazione/importazione del progetto. La serializzazione vive in
 * [ProjectTransferManager]; qui si coordinano I/O in background, conferma e messaggi.
 */
class ProjectTransferViewModel(
    private val manager: ProjectTransferManager,
    private val documents: ProjectDocumentAccess,
    private val host: ProjectReplacementHost,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProjectTransferUiState())
    val uiState: StateFlow<ProjectTransferUiState> = _uiState.asStateFlow()

    private var preparedImport: PreparedProjectImport? = null

    fun onExportDestinationSelected(uri: Uri?) {
        if (uri == null) {
            showMessage("Esportazione annullata", "Nessun file è stato creato.", isError = false)
            return
        }
        if (uiState.value.isBusy) return
        val device = DeviceCatalog.findDevice(host.currentProjectDeviceId) ?: DeviceCatalog.devices.first()

        viewModelScope.launch {
            setBusy("Esportazione del progetto in corso…")
            val result = runIo {
                documents.openOutput(uri).use { output -> manager.export(device, output) }
            }
            result
                .onSuccess { exported ->
                    showMessage(
                        title = "Progetto esportato",
                        text = "Archivio creato con ${exported.audioFileCount} file audio " +
                            "(${ProjectArchiveReader.formatMegabytes(exported.totalAudioBytes)}). " +
                            "Copialo sull'altro smartphone e usa \"Importa progetto\".",
                        isError = false
                    )
                }
                .onFailure { error ->
                    withContext(ioDispatcher) { documents.deleteQuietly(uri) }
                    showMessage("Esportazione non riuscita", error.toUserMessage(), isError = true)
                }
        }
    }

    fun onImportArchiveSelected(uri: Uri?) {
        if (uri == null || uiState.value.isBusy) return
        discardPreparedImport()

        viewModelScope.launch {
            setBusy("Verifica dell'archivio in corso…")
            runIo { documents.openInput(uri).use { input -> manager.prepareImport(input) } }
                .onSuccess { prepared ->
                    preparedImport = prepared
                    _uiState.update {
                        it.copy(busyMessage = null, importPreview = prepared.summary.toPreviewUi())
                    }
                }
                .onFailure { error ->
                    showMessage("Archivio non importabile", error.toUserMessage(), isError = true)
                }
        }
    }

    fun onImportConfirmed() {
        val prepared = preparedImport ?: return
        if (uiState.value.isBusy) return
        preparedImport = null

        viewModelScope.launch {
            _uiState.update { it.copy(importPreview = null, busyMessage = "Importazione del progetto in corso…") }
            host.prepareForProjectReplacement()
            val result = runIo { manager.applyImport(prepared) }
            host.onProjectReplacementFinished()
            result
                .onSuccess {
                    showMessage(
                        title = "Progetto importato",
                        text = "\"${prepared.summary.projectName.ifBlank { "Progetto" }}\" è ora il progetto " +
                            "corrente ed è pronto all'uso.",
                        isError = false
                    )
                }
                .onFailure { error ->
                    showMessage("Importazione non riuscita", error.toUserMessage(), isError = true)
                }
        }
    }

    fun onImportCancelled() {
        discardPreparedImport()
        _uiState.update { it.copy(importPreview = null) }
    }

    fun onDocumentPickerUnavailable() {
        showMessage(
            title = "Selezione file non disponibile",
            text = "Su questo smartphone non è disponibile un'app per scegliere o salvare file.",
            isError = true
        )
    }

    fun onMessageDismissed() {
        _uiState.update { it.copy(message = null) }
    }

    override fun onCleared() {
        preparedImport?.let(manager::discardImport)
        preparedImport = null
        super.onCleared()
    }

    private fun discardPreparedImport() {
        val prepared = preparedImport ?: return
        preparedImport = null
        viewModelScope.launch { runIo { manager.discardImport(prepared) } }
    }

    private suspend fun <T> runIo(block: () -> T): Result<T> {
        return try {
            Result.success(withContext(ioDispatcher) { block() })
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    private fun setBusy(message: String) {
        _uiState.update { it.copy(busyMessage = message, message = null) }
    }

    private fun showMessage(title: String, text: String, isError: Boolean) {
        _uiState.update {
            it.copy(busyMessage = null, message = ProjectTransferMessage(title, text, isError))
        }
    }

    private fun Throwable.toUserMessage(): String {
        return when (this) {
            is ProjectArchiveException -> message.orEmpty()
            is FileNotFoundException -> "Impossibile accedere al file selezionato."
            is SecurityException -> "Permesso negato per il file selezionato."
            else -> "Errore imprevisto: ${message ?: javaClass.simpleName}"
        }
    }

    private fun ProjectImportSummary.toPreviewUi(): ProjectImportPreviewUi {
        val details = buildList {
            add("Attivatori: $trackCount")
            add("Serie: $seriesCount ($enabledSeriesCount attive)")
            add("File audio: $audioFileCount (${ProjectArchiveReader.formatMegabytes(totalAudioBytes)})")
            if (createdAt.isNotBlank()) add("Esportato il: ${createdAt.replace('T', ' ').removeSuffix("Z")} UTC")
            if (sourceAppVersion.isNotBlank()) add("Creato con: $sourceAppVersion (formato v$schemaVersion)")
        }
        val profileLines = profiles.map { profile ->
            profile.label + ": " + profile.languages.joinToString(", ") { language ->
                if (language.enabled) language.label else "${language.label} (disattivata)"
            }
        }
        return ProjectImportPreviewUi(
            projectName = projectName,
            schoolName = schoolName,
            details = details,
            profiles = profileLines,
            warnings = warnings
        )
    }
}

class ProjectTransferViewModelFactory(
    private val manager: ProjectTransferManager,
    private val documents: ProjectDocumentAccess,
    private val host: ProjectReplacementHost
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProjectTransferViewModel::class.java)) {
            return ProjectTransferViewModel(manager, documents, host) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
