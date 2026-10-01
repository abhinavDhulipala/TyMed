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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tymed.app.data.entity.Incident
import com.tymed.app.data.entity.durationSeconds
import com.tymed.app.ui.theme.TymedColors
import com.tymed.app.ui.theme.TymedRadii
import com.tymed.app.ui.theme.TymedSpacing
import com.tymed.app.util.formatDurationSeconds
import com.tymed.app.util.formatInstantDate
import com.tymed.app.util.formatInstantTime

@Composable
fun IncidentCard(incident: Incident, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
        Column {
            Text(text = incident.type, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                text = "${formatInstantDate(incident.startedAt)} · ${formatInstantTime(incident.startedAt)}",
                color = TymedColors.textMuted,
                fontSize = 13.sp,
            )
            incident.notes?.takeIf { it.isNotBlank() }?.let {
                Text(text = it, color = TymedColors.textMuted, fontSize = 13.sp, maxLines = 1)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(text = formatDurationSeconds(incident.durationSeconds()), color = TymedColors.primary, fontSize = 13.sp)
            incident.severity?.let {
                Text(text = it.replaceFirstChar(Char::uppercase), color = TymedColors.textMuted, fontSize = 12.sp)
            }
        }
    }
}
