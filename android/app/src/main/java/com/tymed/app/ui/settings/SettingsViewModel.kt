package com.tymed.app.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tymed.app.AppContainer
import com.tymed.app.data.repository.DEFAULT_FOLLOW_UP_MINUTES
import com.tymed.app.data.repository.ImportData
import com.tymed.app.data.repository.ImportResult
import com.tymed.app.export.ExportFormat
import com.tymed.app.export.render
import com.tymed.app.importer.ImportException
import com.tymed.app.importer.ImportParser
import com.tymed.app.observability.captureException
import com.tymed.app.ui.AiAssistantPreference
import com.tymed.app.util.TimeFormatPreference
import java.io.File
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val use24HourFormat: Boolean = false,
    val aiAssistantEnabled: Boolean = false,
    val followUpMinutesInput: String = DEFAULT_FOLLOW_UP_MINUTES.toString(),
    val importDialog: ImportDialog? = null,
)

/** The import flow's steps, each shown as a dialog: the file is parsed and summarized first, and
 * nothing is written until the user confirms. */
sealed interface ImportDialog {
    data class Confirm(val data: ImportData) : ImportDialog
    data object Importing : ImportDialog
    data class Done(val result: ImportResult) : ImportDialog
    data class Failed(val message: String) : ImportDialog
}

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    init {
        viewModelScope.launch {
            val settings = container.settingsRepository
            val use24HourFormat = settings.getUse24HourFormat()
            val aiAssistantEnabled = settings.getAiAssistantEnabled()
            val followUpMinutes = settings.getFollowUpMinutes()
            _uiState.update {
                it.copy(
                    use24HourFormat = use24HourFormat,
                    aiAssistantEnabled = aiAssistantEnabled,
                    followUpMinutesInput = followUpMinutes.toString(),
                )
            }
            TimeFormatPreference.use24Hour = _uiState.value.use24HourFormat
            AiAssistantPreference.enabled = _uiState.value.aiAssistantEnabled
        }
    }

    fun setUse24HourFormat(value: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.setUse24HourFormat(value)
            TimeFormatPreference.use24Hour = value
            _uiState.update { it.copy(use24HourFormat = value) }
        }
    }

    fun setAiAssistantEnabled(value: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.setAiAssistantEnabled(value)
            AiAssistantPreference.enabled = value
            _uiState.update { it.copy(aiAssistantEnabled = value) }
        }
    }

    fun setFollowUpMinutesInput(value: String) {
        _uiState.update { it.copy(followUpMinutesInput = value.filter { c -> c.isDigit() }) }
    }

    fun saveFollowUpMinutes() {
        val minutes = _uiState.value.followUpMinutesInput.toIntOrNull()?.takeIf { it > 0 } ?: DEFAULT_FOLLOW_UP_MINUTES
        viewModelScope.launch {
            container.settingsRepository.setFollowUpMinutes(minutes)
            container.alarmScheduler.setFollowUpMinutes(minutes)
            _uiState.update { it.copy(followUpMinutesInput = minutes.toString()) }
        }
    }

    /** Renders a full export in the given [format] to a timestamped file under
     * `cacheDir/exports/` and returns it, for the caller to hand to a FileProvider-backed share
     * intent. Runs off the main thread since it touches disk and reads the whole database. */
    suspend fun exportData(format: ExportFormat, cacheDir: File): File = withContext(Dispatchers.IO) {
        val snapshot = container.exportRepository.loadSnapshot()
        val bytes = format.render(snapshot)
        val dir = File(cacheDir, "exports").apply { mkdirs() }
        File(dir, "tymed-export-${Instant.now().epochSecond}.${format.fileExtension}").apply { writeBytes(bytes) }
    }

    /** Reads and validates a picked file, then asks for confirmation — see [confirmImport]. */
    fun loadImportFile(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            val dialog = try {
                val data = withContext(Dispatchers.IO) {
                    val input = contentResolver.openInputStream(uri) ?: throw ImportException("Couldn't open that file.")
                    input.use { ImportParser.parse(it) }
                }
                ImportDialog.Confirm(data)
            } catch (error: ImportException) {
                ImportDialog.Failed(error.message ?: "Couldn't import that file.")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                captureException(error)
                ImportDialog.Failed("Couldn't read that file.")
            }
            _uiState.update { it.copy(importDialog = dialog) }
        }
    }

    fun confirmImport() {
        val confirm = _uiState.value.importDialog as? ImportDialog.Confirm ?: return
        _uiState.update { it.copy(importDialog = ImportDialog.Importing) }
        viewModelScope.launch {
            val dialog = try {
                ImportDialog.Done(withContext(Dispatchers.IO) { container.importRepository.importData(confirm.data) })
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // The merge runs in one transaction, so a failure here leaves the database as it was.
                captureException(error)
                ImportDialog.Failed("The import failed and nothing was changed.")
            }
            _uiState.update { it.copy(importDialog = dialog) }
        }
    }

    fun dismissImportDialog() {
        _uiState.update { it.copy(importDialog = null) }
    }
}
