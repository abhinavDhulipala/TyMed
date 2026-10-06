package com.tymed.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tymed.app.AppContainer
import com.tymed.app.data.repository.DEFAULT_FOLLOW_UP_MINUTES
import com.tymed.app.export.ExportFormat
import com.tymed.app.export.render
import com.tymed.app.ui.AiAssistantPreference
import com.tymed.app.util.TimeFormatPreference
import java.io.File
import java.time.Instant
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
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    init {
        viewModelScope.launch {
            val settings = container.settingsRepository
            _uiState.value = SettingsUiState(
                use24HourFormat = settings.getUse24HourFormat(),
                aiAssistantEnabled = settings.getAiAssistantEnabled(),
                followUpMinutesInput = settings.getFollowUpMinutes().toString(),
            )
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
}
