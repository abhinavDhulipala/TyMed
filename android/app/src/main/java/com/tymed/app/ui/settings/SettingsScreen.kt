package com.tymed.app.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tymed.app.BuildConfig
import com.tymed.app.ui.TymedViewModelFactory
import com.tymed.app.ui.components.Mascot
import com.tymed.app.ui.rememberAppContainer
import com.tymed.app.ui.theme.TymedColors
import com.tymed.app.ui.theme.TymedSpacing

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val container = rememberAppContainer()
    val viewModel: SettingsViewModel = viewModel(factory = TymedViewModelFactory(container) { SettingsViewModel(it) })
    val uiState by viewModel.uiState.collectAsState()
    val aiSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(TymedSpacing.md),
        verticalArrangement = Arrangement.spacedBy(TymedSpacing.lg),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Mascot()
            Text(text = "TyMed", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }

        SettingsSection(title = "Display") {
            SettingsRow(label = "Use 24-hour time") {
                Switch(checked = uiState.use24HourFormat, onCheckedChange = viewModel::setUse24HourFormat)
            }
        }

        SettingsSection(title = "AI Assistant") {
            SettingsRow(label = "Enable on-device assistant") {
                Switch(
                    checked = uiState.aiAssistantEnabled,
                    onCheckedChange = viewModel::setAiAssistantEnabled,
                    enabled = aiSupported,
                )
            }
            if (!aiSupported) {
                Text("Requires Android 8.0 or newer.", color = TymedColors.textMuted, fontSize = 12.sp)
            }
        }

        SettingsSection(title = "Reminders") {
            Text("Follow-up (snooze) interval, in minutes", color = TymedColors.textMuted)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TymedSpacing.sm)) {
                OutlinedTextField(
                    value = uiState.followUpMinutesInput,
                    onValueChange = viewModel::setFollowUpMinutesInput,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = viewModel::saveFollowUpMinutes,
                    colors = ButtonDefaults.buttonColors(containerColor = TymedColors.primary),
                ) { Text("Save") }
            }
        }

        SettingsSection(title = "About") {
            Text(
                "TyMed is not a substitute for professional medical advice. Always follow your " +
                    "prescriber's instructions.",
                color = TymedColors.textMuted,
                fontSize = 12.sp,
            )
            Text("Version ${BuildConfig.VERSION_NAME}", color = TymedColors.textMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(TymedSpacing.sm)) {
        Text(text = title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        content()
    }
}

@Composable
private fun SettingsRow(label: String, control: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        control()
    }
}
