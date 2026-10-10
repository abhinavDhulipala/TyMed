package com.tymed.app.ui.doses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tymed.app.data.entity.DoseStatus
import com.tymed.app.ui.TymedViewModelFactory
import com.tymed.app.ui.components.AdherenceCalendar
import com.tymed.app.ui.components.DoseRow
import com.tymed.app.ui.components.EmptyState
import com.tymed.app.ui.rememberAppContainer
import com.tymed.app.ui.theme.TymedColors
import com.tymed.app.ui.theme.TymedSpacing
import com.tymed.app.util.todayDateString

@Composable
fun TodayScreen(
    onDoseClick: (Long) -> Unit,
    onDayClick: (String) -> Unit,
    onAddMedication: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = rememberAppContainer()
    val today = remember { todayDateString() }
    val profileId = container.activeProfile.current
    val viewModel: DayDosesViewModel = viewModel(
        key = "today-$profileId",
        factory = TymedViewModelFactory(container) { DayDosesViewModel(it, profileId, today) },
    )
    val doses by viewModel.doses.collectAsState()
    val loading by viewModel.loading.collectAsState()

    LifecycleResumeEffect(profileId) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(TymedSpacing.md),
        verticalArrangement = Arrangement.spacedBy(TymedSpacing.sm),
    ) {
        item {
            AdherenceCalendar(intakeLogRepository = container.intakeLogRepository, profileId = profileId, onDayClick = onDayClick)
        }

        if (!loading && doses.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(title = "No medications yet", subtitle = "Add one to start tracking doses.")
                }
            }
            item {
                Button(
                    onClick = onAddMedication,
                    colors = ButtonDefaults.buttonColors(containerColor = TymedColors.primary),
                ) { Text("Add a medication") }
            }
        } else {
            items(doses, key = { it.log.id }) { dose ->
                DoseRow(
                    dose = dose,
                    onClick = { onDoseClick(dose.log.id) },
                    onMarkTaken = { viewModel.markDose(dose.log.id, DoseStatus.TAKEN) },
                    onSkip = { viewModel.markDose(dose.log.id, DoseStatus.SKIPPED) },
                    onUndo = { viewModel.markDose(dose.log.id, DoseStatus.PENDING) },
                )
            }
        }
    }
}
