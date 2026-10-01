package com.tymed.app.ui.incidents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tymed.app.ui.TymedViewModelFactory
import com.tymed.app.ui.components.EmptyState
import com.tymed.app.ui.components.IncidentCard
import com.tymed.app.ui.rememberAppContainer
import com.tymed.app.ui.theme.TymedSpacing

@Composable
fun IncidentsListScreen(
    onIncidentClick: (Long) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = rememberAppContainer()
    val viewModel: IncidentsListViewModel = viewModel(factory = TymedViewModelFactory(container) { IncidentsListViewModel(it) })
    val incidents by viewModel.incidents.collectAsState()

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) { Icon(Icons.Default.Add, contentDescription = "Log incident") }
        },
    ) { padding ->
        if (incidents.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(title = "No incidents logged", subtitle = "Tap + to log a seizure or other event.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(TymedSpacing.md),
                verticalArrangement = Arrangement.spacedBy(TymedSpacing.sm),
            ) {
                items(incidents) { incident ->
                    IncidentCard(incident = incident, onClick = { onIncidentClick(incident.id) })
                }
            }
        }
    }
}
