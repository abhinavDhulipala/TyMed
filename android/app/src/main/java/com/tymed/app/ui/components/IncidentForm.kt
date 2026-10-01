package com.tymed.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.tymed.app.data.entity.IncidentSeverity
import com.tymed.app.ui.incidents.IncidentFormValues
import com.tymed.app.ui.theme.TymedColors
import com.tymed.app.ui.theme.TymedSpacing
import com.tymed.app.util.TimeFormatPreference
import com.tymed.app.util.formatTime

private val TYPE_SUGGESTIONS = listOf("Seizure", "Vomiting", "Other")
private val SEVERITY_OPTIONS = listOf(
    IncidentSeverity.MILD to "Mild",
    IncidentSeverity.MODERATE to "Moderate",
    IncidentSeverity.SEVERE to "Severe",
)

@Composable
fun IncidentForm(
    values: IncidentFormValues,
    isEdit: Boolean,
    onValuesChange: (IncidentFormValues) -> Unit,
    onSubmit: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(TymedSpacing.md),
        verticalArrangement = Arrangement.spacedBy(TymedSpacing.md),
    ) {
        item {
            OutlinedTextField(
                value = values.type,
                onValueChange = { onValuesChange(values.copy(type = it)) },
                label = { Text("Type (e.g. Seizure)") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(TymedSpacing.xs)) {
                TYPE_SUGGESTIONS.forEach { suggestion ->
                    FilterChip(
                        selected = values.type == suggestion,
                        onClick = { onValuesChange(values.copy(type = suggestion)) },
                        label = { Text(suggestion) },
                    )
                }
            }
        }

        item { Text("Started", color = TymedColors.textMuted) }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(TymedSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = values.startDate,
                    onValueChange = { onValuesChange(values.copy(startDate = it)) },
                    label = { Text("Date (YYYY-MM-DD)") },
                    modifier = Modifier.weight(1f),
                )
                TimePickerButton(value = values.startTime, onValueChange = { onValuesChange(values.copy(startTime = it)) })
            }
        }

        item { Text("Duration", color = TymedColors.textMuted) }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(TymedSpacing.sm),
            ) {
                OutlinedTextField(
                    value = values.durationMinutes,
                    onValueChange = { onValuesChange(values.copy(durationMinutes = it.filter(Char::isDigit))) },
                    label = { Text("Minutes") },
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = values.durationSeconds,
                    onValueChange = { onValuesChange(values.copy(durationSeconds = it.filter(Char::isDigit))) },
                    label = { Text("Seconds") },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item { Text("Severity (optional)", color = TymedColors.textMuted) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(TymedSpacing.xs)) {
                SEVERITY_OPTIONS.forEach { (value, label) ->
                    FilterChip(
                        selected = values.severity == value,
                        onClick = { onValuesChange(values.copy(severity = if (values.severity == value) "" else value)) },
                        label = { Text(label) },
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = values.notes,
                onValueChange = { onValuesChange(values.copy(notes = it)) },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Button(
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = TymedColors.primary),
            ) { Text(if (isEdit) "Save changes" else "Log incident") }
        }

        if (isEdit && onDelete != null) {
            item {
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Delete", color = TymedColors.danger) }
            }
        }
    }

    if (showDeleteConfirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this incident?") },
            text = { Text("This removes it from your history. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) { Text("Delete", color = TymedColors.danger) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
        )
    }
}

/** Same time-picker dialog as [TimeOfDayRow], without the "Remove" action that only makes sense
 * for a list of reminder times, not a single start/end moment. */
@Composable
private fun TimePickerButton(value: String, onValueChange: (String) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }

    OutlinedButton(onClick = { showPicker = true }) { Text(formatTime(value)) }

    if (showPicker) {
        val parts = value.split(":")
        val state = rememberTimePickerState(
            initialHour = parts.getOrNull(0)?.toIntOrNull() ?: 0,
            initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0,
            is24Hour = TimeFormatPreference.use24Hour,
        )
        AlertDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onValueChange("%02d:%02d".format(state.hour, state.minute))
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
            text = { TimePicker(state = state) },
        )
    }
}
