package com.tymed.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tymed.app.data.ReminderMute
import com.tymed.app.ui.theme.TymedColors
import com.tymed.app.ui.theme.TymedSpacing
import com.tymed.app.util.formatMutedUntil

private const val MAX_DAYS_DIGITS = 3

/** [onMute] receives the number of days, or null for "until I unmute". */
@Composable
fun MuteDialog(medicationName: String, onMute: (Int?) -> Unit, onDismiss: () -> Unit) {
    var indefinite by rememberSaveable { mutableStateOf(false) }
    var daysText by rememberSaveable { mutableStateOf("7") }
    val days = daysText.toIntOrNull()?.takeIf { it > 0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mute $medicationName?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(TymedSpacing.sm)) {
                Text("Alarms won't ring. Doses still show up and can be marked taken.", color = TymedColors.textMuted)
                Column(modifier = Modifier.selectableGroup()) {
                    MuteOption(selected = !indefinite, onSelect = { indefinite = false }) {
                        Text("For")
                        OutlinedTextField(
                            value = daysText,
                            onValueChange = {
                                daysText = it.filter(Char::isDigit).take(MAX_DAYS_DIGITS)
                                indefinite = false
                            },
                            singleLine = true,
                            isError = !indefinite && days == null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(80.dp),
                        )
                        Text(if (days == 1) "day" else "days")
                    }
                    MuteOption(selected = indefinite, onSelect = { indefinite = true }) { Text("Until I unmute") }
                }
                if (!indefinite && days != null) {
                    Text("Muted ${formatMutedUntil(ReminderMute.untilAfterDays(days))}", color = TymedColors.textMuted)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onMute(if (indefinite) null else days) }, enabled = indefinite || days != null) { Text("Mute") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun MuteOption(selected: Boolean, onSelect: () -> Unit, label: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = TymedSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(TymedSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        label()
    }
}
