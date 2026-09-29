package com.tymed.app.ui.medications

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tymed.app.ui.TymedViewModelFactory
import com.tymed.app.ui.components.MedicationForm
import com.tymed.app.ui.rememberAppContainer

@Composable
fun MedicationFormScreen(
    medicationId: Long?,
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = rememberAppContainer()
    val viewModel: MedicationFormViewModel = viewModel(
        key = "medication-form-$medicationId",
        factory = TymedViewModelFactory(container) { MedicationFormViewModel(it, medicationId) },
    )
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.saved) { if (uiState.saved) onSaved() }
    LaunchedEffect(uiState.deleted) { if (uiState.deleted) onDeleted() }

    if (uiState.loading) {
        CircularProgressIndicator()
        return
    }

    MedicationForm(
        values = uiState.values,
        monthlyAnchorDay = uiState.monthlyAnchorDay,
        isEdit = uiState.isEdit,
        onValuesChange = { newValues -> viewModel.update { newValues } },
        onSubmit = { viewModel.submit() },
        onDelete = if (uiState.isEdit) viewModel::delete else null,
        modifier = modifier,
    )

    uiState.duplicateExactMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDuplicateWarning,
            title = { Text("Already added") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::dismissDuplicateWarning) { Text("OK") } },
        )
    }

    uiState.duplicatePartialNames?.let { names ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDuplicateWarning,
            title = { Text("Similar medication exists") },
            text = { Text("You already have ${names.joinToString(", ")} with a different dosage or form. Add anyway?") },
            confirmButton = { TextButton(onClick = { viewModel.submit(force = true) }) { Text("Add anyway") } },
            dismissButton = { TextButton(onClick = viewModel::dismissDuplicateWarning) { Text("Cancel") } },
        )
    }
}
