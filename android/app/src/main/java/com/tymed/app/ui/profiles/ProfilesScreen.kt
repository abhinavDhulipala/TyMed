package com.tymed.app.ui.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tymed.app.data.entity.Profile
import com.tymed.app.ui.TymedViewModelFactory
import com.tymed.app.ui.rememberAppContainer
import com.tymed.app.ui.theme.TymedColors
import com.tymed.app.ui.theme.TymedSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val container = rememberAppContainer()
    val viewModel: ProfilesViewModel = viewModel(factory = TymedViewModelFactory(container) { ProfilesViewModel(it) })
    val uiState by viewModel.uiState.collectAsState()
    var addingProfile by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<Profile?>(null) }
    var deletingProfile by remember { mutableStateOf<Profile?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Profiles") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { addingProfile = true }) { Icon(Icons.Default.Add, contentDescription = "Add profile") }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(TymedSpacing.md),
            verticalArrangement = Arrangement.spacedBy(TymedSpacing.sm),
        ) {
            items(uiState.profiles, key = { it.id }) { profile ->
                ProfileRow(
                    profile = profile,
                    onEdit = { editingProfile = profile },
                    onDelete = { deletingProfile = profile },
                )
            }
        }
    }

    if (addingProfile) {
        ProfileNameDialog(
            title = "Add profile",
            initialName = "",
            onConfirm = { name ->
                viewModel.addProfile(name)
                addingProfile = false
            },
            onDismiss = { addingProfile = false },
        )
    }

    editingProfile?.let { profile ->
        ProfileNameDialog(
            title = "Rename profile",
            initialName = profile.name,
            onConfirm = { name ->
                viewModel.renameProfile(profile.id, name, profile.colorHex)
                editingProfile = null
            },
            onDismiss = { editingProfile = null },
        )
    }

    deletingProfile?.let { profile ->
        AlertDialog(
            onDismissRequest = { deletingProfile = null },
            title = { Text("Delete ${profile.name}?") },
            text = { Text("This permanently deletes every medication, schedule, dose history, and incident for ${profile.name}.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteProfile(profile.id)
                    deletingProfile = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deletingProfile = null }) { Text("Cancel") } },
        )
    }

    uiState.deleteBlockedMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteBlockedMessage,
            title = { Text("Can't delete") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::dismissDeleteBlockedMessage) { Text("OK") } },
        )
    }
}

@Composable
private fun ProfileRow(profile: Profile, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TymedSpacing.sm)) {
            ProfileAvatar(profile)
            Text(profile.name, fontWeight = FontWeight.SemiBold)
        }
        Row {
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Rename ${profile.name}") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete ${profile.name}") }
        }
    }
}

@Composable
fun ProfileAvatar(profile: Profile, size: Dp = 36.dp) {
    val color = remember(profile.colorHex) { parseHexColor(profile.colorHex) }
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = profile.name.trim().take(1).uppercase(),
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
    }
}

fun parseHexColor(hex: String): Color =
    try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (error: IllegalArgumentException) {
        TymedColors.primary
    }

@Composable
private fun ProfileNameDialog(title: String, initialName: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") })
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
