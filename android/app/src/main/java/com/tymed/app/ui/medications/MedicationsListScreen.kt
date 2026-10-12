package com.tymed.app.ui.medications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tymed.app.ui.TymedViewModelFactory
import com.tymed.app.ui.components.EmptyState
import com.tymed.app.ui.components.MedicationCard
import com.tymed.app.ui.rememberAppContainer
import com.tymed.app.ui.theme.TymedSpacing

@Composable
fun MedicationsListScreen(
    onMedicationClick: (Long) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = rememberAppContainer()
    val viewModel: MedicationsListViewModel = viewModel(factory = TymedViewModelFactory(container) { MedicationsListViewModel(it) })
    val medications by viewModel.medications.collectAsState()
    val profileId = container.activeProfile.current

    LaunchedEffect(profileId) { viewModel.refresh(profileId) }
    LifecycleResumeEffect(profileId) {
        viewModel.refresh(profileId)
        onPauseOrDispose { }
    }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) { Icon(Icons.Default.Add, contentDescription = "Add medication") }
        },
    ) { padding ->
        if (medications.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(title = "No medications yet", subtitle = "Tap + to add your first one.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(TymedSpacing.md),
                verticalArrangement = Arrangement.spacedBy(TymedSpacing.sm),
            ) {
                items(medications) { medication ->
                    MedicationCard(medication = medication, onClick = { onMedicationClick(medication.id) })
                }
            }
        }
    }
}
