package com.tymed.app.ui.doses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tymed.app.data.entity.DoseStatus
import com.tymed.app.ui.TymedViewModelFactory
import com.tymed.app.ui.rememberAppContainer
import com.tymed.app.ui.theme.TymedColors
import com.tymed.app.ui.theme.TymedSpacing
import com.tymed.app.util.formatFullDateLabel
import com.tymed.app.util.formatInstantTime
import com.tymed.app.util.formatTime
import java.time.Instant

@Composable
fun DoseScreen(logId: Long, onEditMedication: (Long) -> Unit, modifier: Modifier = Modifier) {
    val container = rememberAppContainer()
    val viewModel: DoseViewModel = viewModel(
        key = "dose-$logId",
        factory = TymedViewModelFactory(container) { DoseViewModel(it, logId) },
    )
    val dose by viewModel.dose.collectAsState()
    val current = dose ?: return

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(TymedSpacing.md),
        verticalArrangement = Arrangement.spacedBy(TymedSpacing.md),
    ) {
        Text(text = formatFullDateLabel(current.log.scheduledDate), fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text(text = formatTime(current.log.scheduledTime), fontSize = 16.sp, color = TymedColors.textMuted)
        Text(text = current.medicationName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        current.dosage?.let { Text(text = it, color = TymedColors.textMuted) }

        Text(text = "Status: ${current.log.status}", color = TymedColors.text)

        if (current.log.status == DoseStatus.TAKEN && current.log.takenAt != null) {
            Text(text = "Taken at ${formatInstantTime(current.log.takenAt)}", color = TymedColors.textMuted)

            var showTimePicker by remember { mutableStateOf(false) }
            TextButton(onClick = { showTimePicker = true }) { Text("Correct taken time") }
            if (showTimePicker) {
                val zone = java.time.ZoneId.systemDefault()
                val existing = try {
                    Instant.parse(current.log.takenAt).atZone(zone)
                } catch (error: Exception) {
                    java.time.ZonedDateTime.now(zone)
                }
                val state = rememberTimePickerState(initialHour = existing.hour, initialMinute = existing.minute)
                AlertDialog(
                    onDismissRequest = { showTimePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            val corrected = existing.withHour(state.hour).withMinute(state.minute).withSecond(0).withNano(0)
                            viewModel.setTakenAt(corrected.toInstant().toString())
                            showTimePicker = false
                        }) { Text("OK") }
                    },
                    dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } },
                    text = { TimePicker(state = state) },
                )
            }
        }

        if (current.log.status == DoseStatus.PENDING) {
            Button(
                onClick = { viewModel.markDose(DoseStatus.TAKEN) },
                colors = ButtonDefaults.buttonColors(containerColor = TymedColors.primary),
            ) { Text("Mark taken") }
            OutlinedButton(onClick = { viewModel.markDose(DoseStatus.SKIPPED) }) { Text("Skip") }
        } else {
            TextButton(onClick = { viewModel.markDose(DoseStatus.PENDING) }) { Text("Undo — mark pending again") }
        }

        OutlinedButton(onClick = { onEditMedication(current.log.medicationId) }) {
            Text("Edit ${current.medicationName}")
        }
    }
}
