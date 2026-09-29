package com.tymed.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tymed.app.AppContainer
import com.tymed.app.data.repository.DEFAULT_FOLLOW_UP_MINUTES
import com.tymed.app.util.TimeFormatPreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
}
