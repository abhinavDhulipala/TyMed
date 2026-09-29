package com.tymed.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tymed.app.ui.theme.TymedColors
import com.tymed.app.ui.theme.TymedSpacing

@Composable
fun EmptyState(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(TymedSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TymedSpacing.sm),
    ) {
        Mascot(size = 96.dp)
        Text(text = title, fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = TextAlign.Center)
        subtitle?.let {
            Text(text = it, color = TymedColors.textMuted, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
    }
}
