package com.tymed.app.ui.settings

import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tymed.app.BuildConfig
import com.tymed.app.data.repository.ImportData
import com.tymed.app.data.repository.ImportResult
import com.tymed.app.export.ExportFormat
import com.tymed.app.ui.TymedViewModelFactory
import com.tymed.app.ui.components.Mascot
import com.tymed.app.ui.rememberAppContainer
import com.tymed.app.ui.theme.TymedColors
import com.tymed.app.ui.theme.TymedSpacing
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val container = rememberAppContainer()
    val profileId = container.activeProfile.current
    val viewModel: SettingsViewModel = viewModel(
        key = "settings-$profileId",
        factory = TymedViewModelFactory(container) { SettingsViewModel(it, profileId) },
    )
    val uiState by viewModel.uiState.collectAsState()
    val aiSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    // "*/*" rather than JSON/zip MIME types: providers label these files inconsistently
    // (octet-stream, text/plain, x-zip-compressed...), and a filter that greys out the user's
    // own export is worse than ImportParser rejecting a wrong pick with a clear message.
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.loadImportFile(context.contentResolver, uri)
    }
    var exportScope by remember { mutableStateOf(ExportScope.ACTIVE_PROFILE) }

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

        SettingsSection(title = "Data") {
            Text(
                "Export every medication, schedule, dose log, and incident — as JSON, a zipped " +
                    "CSV per table, or a printable PDF report.",
                color = TymedColors.textMuted,
                fontSize = 12.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(TymedSpacing.sm)) {
                FilterChip(
                    selected = exportScope == ExportScope.ACTIVE_PROFILE,
                    onClick = { exportScope = ExportScope.ACTIVE_PROFILE },
                    label = { Text("This profile") },
                )
                FilterChip(
                    selected = exportScope == ExportScope.ALL_PROFILES,
                    onClick = { exportScope = ExportScope.ALL_PROFILES },
                    label = { Text("All profiles") },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(TymedSpacing.sm)) {
                ExportFormat.entries.forEach { format ->
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val file = viewModel.exportData(format, exportScope, context.cacheDir)
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = format.mimeType
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Export TyMed data"))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TymedColors.primary),
                    ) { Text(format.label) }
                }
            }
            Text(
                "Import a JSON or CSV export to restore your medications and dose history. Existing " +
                    "data is kept — anything already on this device is skipped.",
                color = TymedColors.textMuted,
                fontSize = 12.sp,
            )
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) { Text("Import from file") }
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

    ImportDialogs(uiState.importDialog, onConfirm = viewModel::confirmImport, onDismiss = viewModel::dismissImportDialog)
}

@Composable
private fun ImportDialogs(dialog: ImportDialog?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    when (dialog) {
        null -> Unit
        is ImportDialog.Confirm -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Import data?") },
            text = { Text(describeImport(dialog.data)) },
            confirmButton = { TextButton(onClick = onConfirm) { Text("Import") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
        // No buttons: dismissing mid-import wouldn't stop it, just hide that it's still running.
        ImportDialog.Importing -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Importing…") },
            text = { Text("Adding your data. This only takes a moment.") },
            confirmButton = {},
        )
        is ImportDialog.Done -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Import complete") },
            text = { Text(describeResult(dialog.result)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
        is ImportDialog.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Couldn't import") },
            text = { Text(dialog.message) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
    }
}

private fun describeImport(data: ImportData): String {
    val contents = listOf(
        count(data.profiles.sumOf { it.medications.size }, "medication"),
        count(data.profiles.sumOf { it.schedules.size }, "schedule"),
        count(data.profiles.sumOf { it.intakeLogs.size }, "dose record"),
        count(data.profiles.sumOf { it.incidents.size }, "incident"),
    )
    val profileNote = if (data.profiles.size > 1) {
        " across ${count(data.profiles.size, "profile")}"
    } else {
        ""
    }
    return "This file has ${contents.joinToString(", ")}$profileNote. They'll be added alongside your current data — " +
        "anything already on this device is skipped, and nothing is deleted."
}

private fun describeResult(result: ImportResult): String {
    val added = listOf(
        count(result.medicationsAdded, "medication"),
        count(result.schedulesAdded, "schedule"),
        count(result.dosesAdded, "dose record"),
        count(result.incidentsAdded, "incident"),
    )
    return buildString {
        append("Added ${added.joinToString(", ")}.")
        if (result.dosesUpdated > 0) append(" Updated ${count(result.dosesUpdated, "pending dose")} with what was recorded.")
        if (result.skipped > 0) append(" Skipped ${count(result.skipped, "record")} already on this device.")
    }
}

private fun count(n: Int, noun: String) = "$n ${if (n == 1) noun else "${noun}s"}"

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
