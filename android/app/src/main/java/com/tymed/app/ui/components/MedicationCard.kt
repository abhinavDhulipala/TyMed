package com.tymed.app.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.sp
import com.tymed.app.data.entity.Medication
import com.tymed.app.ui.theme.TymedColors
import com.tymed.app.ui.theme.TymedRadii
import com.tymed.app.ui.theme.TymedSpacing

@Composable
fun MedicationCard(medication: Medication, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(TymedColors.card, RoundedCornerShape(TymedRadii.md))
            .padding(TymedSpacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(text = medication.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            val subtitle = listOfNotNull(medication.dosage, medication.form).joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(text = subtitle, color = TymedColors.textMuted, fontSize = 13.sp)
            }
        }
        PillCountBadge(pillsRemaining = medication.pillsRemaining, refillThreshold = medication.refillThreshold)
    }
}
