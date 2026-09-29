package com.tymed.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.tymed.app.data.entity.RecurrenceType
import com.tymed.app.ui.medications.ALL_DAYS
import com.tymed.app.ui.medications.MedicationFormValues
import com.tymed.app.ui.theme.TymedColors
import com.tymed.app.ui.theme.TymedSpacing

private val RECURRENCE_OPTIONS = listOf(
    RecurrenceType.DAILY to "Daily",
    RecurrenceType.WEEKLY to "Weekly",
    RecurrenceType.MONTHLY to "Monthly",
)

@Composable
fun MedicationForm(
    values: MedicationFormValues,
    monthlyAnchorDay: Int?,
    isEdit: Boolean,
    onValuesChange: (MedicationFormValues) -> Unit,
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
                value = values.name,
                onValueChange = { onValuesChange(values.copy(name = it)) },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = values.dosage,
                onValueChange = { onValuesChange(values.copy(dosage = it)) },
                label = { Text("Dosage (e.g. 10mg)") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = values.form,
                onValueChange = { onValuesChange(values.copy(form = it)) },
                label = { Text("Form (e.g. tablet)") },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item { Text("Reminder times", color = TymedColors.textMuted) }
        items(values.times) { time ->
            TimeOfDayRow(
                value = time,
                onValueChange = { newTime ->
                    onValuesChange(values.copy(times = values.times.map { if (it == time) newTime else it }))
                },
                onRemove = {
                    if (values.times.size > 1) {
                        onValuesChange(values.copy(times = values.times.filterNot { it == time }))
                    }
                },
            )
        }
        item {
            TextButton(onClick = { onValuesChange(values.copy(times = values.times + "08:00")) }) {
                Text("+ Add time")
            }
        }

        item { Text("Repeats", color = TymedColors.textMuted) }
        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                RECURRENCE_OPTIONS.forEachIndexed { index, (type, label) ->
                    SegmentedButton(
                        selected = values.recurrenceType == type,
                        onClick = { onValuesChange(values.copy(recurrenceType = type)) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = RECURRENCE_OPTIONS.size),
                    ) { Text(label) }
                }
            }
        }

        if (values.recurrenceType == RecurrenceType.WEEKLY) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(TymedSpacing.xs)) {
                    ALL_DAYS.forEachIndexed { index, label ->
                        FilterChip(
                            selected = values.daysOfWeek.contains(index),
                            onClick = {
                                val next = values.daysOfWeek.toMutableSet()
                                if (next.contains(index)) {
                                    // Keep at least one day selected.
                                    if (next.size > 1) next.remove(index)
                                } else {
                                    next.add(index)
                                }
                                onValuesChange(values.copy(daysOfWeek = next))
                            },
                            label = { Text(label) },
                        )
                    }
                }
            }
        }

        if (values.recurrenceType == RecurrenceType.MONTHLY) {
            item {
                val anchor = monthlyAnchorDay ?: java.time.LocalDate.now().dayOfMonth
                Text("Repeats on the ${ordinal(anchor)} of every month", color = TymedColors.textMuted)
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Ends on a date")
                Switch(checked = values.hasEndDate, onCheckedChange = { onValuesChange(values.copy(hasEndDate = it)) })
            }
        }
        if (values.hasEndDate) {
            item {
                OutlinedTextField(
                    value = values.endDate,
                    onValueChange = { onValuesChange(values.copy(endDate = it)) },
                    label = { Text("End date (YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            OutlinedTextField(
                value = values.pillsRemaining,
                onValueChange = { onValuesChange(values.copy(pillsRemaining = it.filter { c -> c.isDigit() })) },
                label = { Text("Pills remaining (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = values.refillThreshold,
                onValueChange = { onValuesChange(values.copy(refillThreshold = it.filter { c -> c.isDigit() })) },
                label = { Text("Refill threshold (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )
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
            ) { Text(if (isEdit) "Save changes" else "Add medication") }
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
            title = { Text("Delete this medication?") },
            text = { Text("This removes its reminders and history. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) { Text("Delete", color = TymedColors.danger) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
        )
    }
}

private fun ordinal(day: Int): String {
    if (day in 11..13) return "${day}th"
    return when (day % 10) {
        1 -> "${day}st"
        2 -> "${day}nd"
        3 -> "${day}rd"
        else -> "${day}th"
    }
}
