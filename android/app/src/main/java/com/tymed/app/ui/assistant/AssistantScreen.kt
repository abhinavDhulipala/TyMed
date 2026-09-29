package com.tymed.app.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tymed.app.ui.TymedViewModelFactory
import com.tymed.app.ui.components.EmptyState
import com.tymed.app.ui.rememberAppContainer
import com.tymed.app.ui.theme.TymedColors
import com.tymed.app.ui.theme.TymedRadii
import com.tymed.app.ui.theme.TymedSpacing

@Composable
fun AssistantScreen(modifier: Modifier = Modifier) {
    val container = rememberAppContainer()
    val viewModel: AssistantViewModel = viewModel(factory = TymedViewModelFactory(container) { AssistantViewModel(it) })
    val uiState by viewModel.uiState.collectAsState()

    LifecycleResumeEffect(Unit) {
        viewModel.checkAndPrepare()
        onPauseOrDispose { }
    }

    when (uiState.phase) {
        AssistantPhase.CHECKING, AssistantPhase.PREPARING -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text("Preparing on-device assistant…", color = TymedColors.textMuted)
                }
            }
        }
        AssistantPhase.DISABLED -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = "Assistant is turned off",
                    subtitle = "Enable it from Settings to chat about your medications.",
                )
            }
        }
        AssistantPhase.PREPARE_FAILED -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = "Assistant unavailable",
                    subtitle = "This device doesn't support the on-device model right now.",
                )
            }
        }
        AssistantPhase.READY -> {
            Column(modifier = modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(TymedSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(TymedSpacing.sm),
                ) {
                    items(uiState.messages) { message ->
                        ChatBubble(message)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(TymedSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(TymedSpacing.sm),
                ) {
                    OutlinedTextField(
                        value = uiState.input,
                        onValueChange = viewModel::setInput,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Ask about your medications…") },
                    )
                    Button(
                        onClick = viewModel::send,
                        enabled = !uiState.sending && uiState.input.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = TymedColors.primary),
                    ) { Text("Send") }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val background = if (message.fromUser) TymedColors.primaryMuted else TymedColors.card
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start,
    ) {
        Text(
            text = message.text,
            modifier = Modifier
                .background(background, RoundedCornerShape(TymedRadii.md))
                .padding(TymedSpacing.sm),
        )
    }
}
