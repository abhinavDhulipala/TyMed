package com.tymed.app.ui.doses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tymed.app.data.entity.DoseStatus
import com.tymed.app.ui.TymedViewModelFactory
import com.tymed.app.ui.components.DoseRow
import com.tymed.app.ui.rememberAppContainer
import com.tymed.app.ui.theme.TymedSpacing

@Composable
fun DayScreen(dateStr: String, onDoseClick: (Long) -> Unit, modifier: Modifier = Modifier) {
    val container = rememberAppContainer()
    val profileId = container.activeProfile.current
    val viewModel: DayDosesViewModel = viewModel(
        key = "day-$dateStr-$profileId",
        factory = TymedViewModelFactory(container) { DayDosesViewModel(it, profileId, dateStr) },
    )
    val doses by viewModel.doses.collectAsState()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(TymedSpacing.md),
        verticalArrangement = Arrangement.spacedBy(TymedSpacing.sm),
    ) {
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
