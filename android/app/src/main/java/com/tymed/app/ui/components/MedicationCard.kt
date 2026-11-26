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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tymed.app.data.ReminderMute
import com.tymed.app.data.entity.Medication
import com.tymed.app.ui.theme.TymedColors
import com.tymed.app.ui.theme.TymedRadii
import com.tymed.app.ui.theme.TymedSpacing
import com.tymed.app.util.formatMutedUntil

@Composable
fun MedicationCard(medication: Medication, onClick: () -> Unit, onMute: () -> Unit, onUnmute: () -> Unit, modifier: Modifier = Modifier) {
    val mutedUntil = medication.mutedUntil?.takeIf { ReminderMute.activeUntil(it) != null }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(TymedColors.card, RoundedCornerShape(TymedRadii.md))
            .border(1.dp, TymedColors.borderStrong, RoundedCornerShape(TymedRadii.md))
            .padding(TymedSpacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = medication.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            val subtitle = listOfNotNull(medication.dosage, medication.form).joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(text = subtitle, color = TymedColors.textMuted, fontSize = 13.sp)
            }
            if (mutedUntil != null) {
                Text(text = "Muted ${formatMutedUntil(mutedUntil)}", color = TymedColors.warning, fontSize = 13.sp)
            }
        }
        PillCountBadge(pillsRemaining = medication.pillsRemaining, refillThreshold = medication.refillThreshold)
        IconButton(onClick = if (mutedUntil != null) onUnmute else onMute) {
            Icon(
                imageVector = if (mutedUntil != null) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                contentDescription = if (mutedUntil != null) "Unmute ${medication.name}" else "Mute ${medication.name}",
                tint = if (mutedUntil != null) TymedColors.warning else TymedColors.textMuted,
            )
        }
    }
}
