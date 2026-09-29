package com.tymed.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tymed.app.ui.theme.TymedColors

@Composable
fun PillCountBadge(pillsRemaining: Int?, refillThreshold: Int?, modifier: Modifier = Modifier) {
    if (pillsRemaining == null) return

    val lowStock = refillThreshold != null && pillsRemaining <= refillThreshold
    val background = if (lowStock) TymedColors.warningMuted else TymedColors.primaryMuted
    val label = if (lowStock) "Refill soon · $pillsRemaining left" else "$pillsRemaining left"

    Text(
        text = label,
        fontSize = 12.sp,
        color = if (lowStock) TymedColors.warning else TymedColors.primary,
        modifier = modifier
            .background(background, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
