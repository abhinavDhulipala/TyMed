package com.tymed.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(TymedColors.card, RoundedCornerShape(TymedRadii.md))
            .padding(TymedSpacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
            }) { Text("‹") }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = monthLabel(year, month), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                monthPercent?.let {
                    Text(text = "$it% taken", color = TymedColors.textMuted, fontSize = 12.sp)
                }
            }

            IconButton(onClick = {
                if (month == 12) {
                    month = 1
                    year += 1
                } else {
                    month += 1
                }
            }) { Text("›") }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = TymedSpacing.sm)) {
            WEEKDAY_LABELS.forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = TymedColors.textMuted,
                    fontSize = 11.sp,
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
                            Canvas(modifier = Modifier.size(28.dp)) {
                                val fraction = dayAdherence.taken.toFloat() / dayAdherence.resolved.toFloat()
                                drawArc(
                                    color = TymedColors.border,
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
                        Text(
                            text = day.day.toString(),
                            fontSize = 12.sp,
                            fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (day.inCurrentMonth) TymedColors.text else TymedColors.textMuted,
                        )
                    }
                }
            }
        }
    }
}
