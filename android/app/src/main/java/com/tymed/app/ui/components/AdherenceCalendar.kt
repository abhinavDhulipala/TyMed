package com.tymed.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tymed.app.data.repository.DailyAdherence
import com.tymed.app.data.repository.IntakeLogRepository
import com.tymed.app.ui.theme.TymedColors
import com.tymed.app.ui.theme.TymedRadii
import com.tymed.app.ui.theme.TymedSpacing
import com.tymed.app.util.WEEKDAY_LABELS
import com.tymed.app.util.buildMonthGrid
import com.tymed.app.util.monthLabel
import java.time.LocalDate

@Composable
fun AdherenceCalendar(
    intakeLogRepository: IntakeLogRepository,
    onDayClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = remember { LocalDate.now() }
    var year by remember { mutableIntStateOf(today.year) }
    var month by remember { mutableIntStateOf(today.monthValue) }

    val grid = remember(year, month) { buildMonthGrid(year, month) }
    val adherence by produceState<Map<String, DailyAdherence>>(initialValue = emptyMap(), year, month) {
        value = intakeLogRepository.getDailyAdherence(grid.first().dateStr, grid.last().dateStr)
    }

    val monthTaken = grid.filter { it.inCurrentMonth }.sumOf { adherence[it.dateStr]?.taken ?: 0 }
    val monthResolved = grid.filter { it.inCurrentMonth }.sumOf { adherence[it.dateStr]?.resolved ?: 0 }
    val monthPercent = if (monthResolved > 0) (monthTaken * 100 / monthResolved) else null

    val shape = RoundedCornerShape(TymedRadii.lg)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(TymedColors.sand)
            .border(1.5.dp, TymedColors.borderStrong, shape),
    ) {
        // Terracotta header band: gives the card a warm, clearly defined top edge on the cream page.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TymedColors.primary)
                .padding(horizontal = TymedSpacing.xs, vertical = TymedSpacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {
                if (month == 1) {
                    month = 12
                    year -= 1
                } else {
                    month -= 1
                }
            }) { Text("‹", color = TymedColors.primaryMuted, fontSize = 24.sp) }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = monthLabel(year, month), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                monthPercent?.let {
                    Text(text = "$it% taken", color = TymedColors.primaryMuted, fontSize = 12.sp)
                }
            }

            IconButton(onClick = {
                if (month == 12) {
                    month = 1
                    year += 1
                } else {
                    month += 1
                }
            }) { Text("›", color = TymedColors.primaryMuted, fontSize = 24.sp) }
        }

        Column(modifier = Modifier.padding(horizontal = TymedSpacing.sm).padding(bottom = TymedSpacing.sm)) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = TymedSpacing.sm, bottom = TymedSpacing.xs)) {
                WEEKDAY_LABELS.forEach { label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        color = TymedColors.textMuted,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                    )
                }
            }

            grid.chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        val dayAdherence = adherence[day.dateStr]
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clickable(enabled = day.inCurrentMonth) { onDayClick(day.dateStr) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (day.inCurrentMonth && !day.isFuture && dayAdherence != null && dayAdherence.resolved > 0) {
                                Canvas(modifier = Modifier.size(32.dp)) {
                                    val fraction = dayAdherence.taken.toFloat() / dayAdherence.resolved.toFloat()
                                    drawArc(
                                        color = TymedColors.borderStrong,
                                        startAngle = -90f,
                                        sweepAngle = 360f,
                                        useCenter = false,
                                        style = Stroke(width = 3.dp.toPx()),
                                    )
                                    drawArc(
                                        color = TymedColors.success,
                                        startAngle = -90f,
                                        sweepAngle = 360f * fraction,
                                        useCenter = false,
                                        style = Stroke(width = 3.dp.toPx()),
                                    )
                                }
                            }
                            if (day.isToday) {
                                Box(modifier = Modifier.size(24.dp).background(TymedColors.primary, CircleShape))
                            }
                            Text(
                                text = day.day.toString(),
                                fontSize = 13.sp,
                                fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Medium,
                                color = when {
                                    day.isToday -> Color.White
                                    day.inCurrentMonth -> TymedColors.text
                                    else -> TymedColors.textMuted
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
