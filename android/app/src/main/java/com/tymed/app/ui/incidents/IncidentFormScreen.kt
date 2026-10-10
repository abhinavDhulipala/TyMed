package com.tymed.app.ui.incidents

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tymed.app.ui.TymedViewModelFactory
import com.tymed.app.ui.components.IncidentForm
import com.tymed.app.ui.rememberAppContainer

@Composable
fun IncidentFormScreen(
    incidentId: Long?,
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = rememberAppContainer()
    val profileId = container.activeProfile.current
    val viewModel: IncidentFormViewModel = viewModel(
        key = "incident-form-$incidentId",
        factory = TymedViewModelFactory(container) { IncidentFormViewModel(it, profileId, incidentId) },
    )
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.saved) { if (uiState.saved) onSaved() }
    LaunchedEffect(uiState.deleted) { if (uiState.deleted) onDeleted() }

    if (uiState.loading) {
        CircularProgressIndicator()
        return
    }

    IncidentForm(
        values = uiState.values,
        isEdit = uiState.isEdit,
        onValuesChange = { newValues -> viewModel.update { newValues } },
        onSubmit = viewModel::submit,
        onDelete = if (uiState.isEdit) viewModel::delete else null,
        modifier = modifier,
    )
}
