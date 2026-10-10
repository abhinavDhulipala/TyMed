package com.tymed.app.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tymed.app.AppContainer
import com.tymed.app.ai.HistoryEntry
import com.tymed.app.ai.PendingAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AssistantPhase { CHECKING, DISABLED, PREPARING, PREPARE_FAILED, READY }

data class ChatMessage(val fromUser: Boolean, val text: String)

data class AssistantUiState(
    val phase: AssistantPhase = AssistantPhase.CHECKING,
    val messages: List<ChatMessage> = emptyList(),
    val sending: Boolean = false,
    val input: String = "",
)

class AssistantViewModel(private val container: AppContainer) : ViewModel() {
    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState

    private var history: List<HistoryEntry> = emptyList()
    private var pending: PendingAction? = null

    fun checkAndPrepare() {
        viewModelScope.launch {
            val enabled = container.settingsRepository.getAiAssistantEnabled(container.activeProfile.current)
            if (!enabled) {
                _uiState.update { it.copy(phase = AssistantPhase.DISABLED) }
                return@launch
            }
            if (_uiState.value.phase == AssistantPhase.READY || _uiState.value.phase == AssistantPhase.PREPARING) return@launch

            _uiState.update { it.copy(phase = AssistantPhase.PREPARING) }
            val ready = container.geminiNanoClient.prepare()
            _uiState.update { it.copy(phase = if (ready) AssistantPhase.READY else AssistantPhase.PREPARE_FAILED) }
        }
    }

    fun setInput(value: String) {
        _uiState.update { it.copy(input = value) }
    }

    fun send() {
        val text = _uiState.value.input.trim()
        if (text.isEmpty() || _uiState.value.sending) return

        _uiState.update {
            it.copy(
                messages = it.messages + ChatMessage(fromUser = true, text = text),
                input = "",
                sending = true,
            )
        }

        viewModelScope.launch {
            val result = container.aiOrchestrator.runTurn(text, history, pending)
            history = result.history
            pending = result.pending
            _uiState.update {
                it.copy(
                    messages = it.messages + ChatMessage(fromUser = false, text = result.reply),
                    sending = false,
                )
            }
        }
    }
}
