package com.tymed.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tymed.app.data.dao.DoseWithMedication
import com.tymed.app.data.entity.DoseStatus
import com.tymed.app.ui.theme.TymedColors
import com.tymed.app.ui.theme.TymedRadii
import com.tymed.app.ui.theme.TymedSpacing
import com.tymed.app.util.formatTime

@Composable
fun DoseRow(
    dose: DoseWithMedication,
    onClick: () -> Unit,
    onMarkTaken: () -> Unit,
    onSkip: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (statusLabel, statusColor, statusBackground) = when (dose.log.status) {
        DoseStatus.TAKEN -> Triple("Taken", TymedColors.success, TymedColors.successMuted)
        DoseStatus.SKIPPED -> Triple("Skipped", TymedColors.textMuted, TymedColors.border)
        else -> Triple("Pending", TymedColors.primary, TymedColors.primaryMuted)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(TymedColors.card, RoundedCornerShape(TymedRadii.md))
            .border(1.dp, TymedColors.borderStrong, RoundedCornerShape(TymedRadii.md))
            .padding(TymedSpacing.md),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(text = formatTime(dose.log.scheduledTime), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(text = dose.medicationName, color = TymedColors.text, fontSize = 14.sp)
                dose.dosage?.let { Text(text = it, color = TymedColors.textMuted, fontSize = 13.sp) }
            }
            Text(
                text = statusLabel,
                color = statusColor,
                fontSize = 12.sp,
                modifier = Modifier
                    .background(statusBackground, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = TymedSpacing.sm),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (dose.log.status == DoseStatus.PENDING) {
                OutlinedButton(onClick = onSkip) { Text("Skip") }
                Button(
                    onClick = onMarkTaken,
                    modifier = Modifier.padding(start = TymedSpacing.sm),
                    colors = ButtonDefaults.buttonColors(containerColor = TymedColors.primary),
                ) { Text("Mark taken") }
            } else {
                TextButton(onClick = onUndo) { Text("Undo — mark pending again") }
            }
        }
    }
}
