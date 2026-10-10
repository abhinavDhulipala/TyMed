package com.tymed.app.ui.profiles

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tymed.app.data.ProfilePhotoStore
import com.tymed.app.data.entity.Profile
import com.tymed.app.ui.TymedViewModelFactory
import com.tymed.app.ui.rememberAppContainer
import com.tymed.app.ui.theme.TymedColors
import com.tymed.app.ui.theme.TymedSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        ProfileEditDialog(
            title = "Add profile",
            initialName = "",
            initialPhotoPath = null,
            onConfirm = { name, photoPath ->
                viewModel.addProfile(name, photoPath)
                addingProfile = false
            },
            onDismiss = { addingProfile = false },
        )
    }

    editingProfile?.let { profile ->
        ProfileEditDialog(
            title = "Edit profile",
            initialName = profile.name,
            initialPhotoPath = profile.photoPath,
            onConfirm = { name, photoPath ->
                viewModel.updateProfile(profile.id, name, profile.colorHex, photoPath)
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
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit ${profile.name}") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete ${profile.name}") }
        }
    }
}

@Composable
fun ProfileAvatar(profile: Profile, size: Dp = 36.dp) {
    val photoBitmap = profile.photoPath?.let { path -> remember(path) { decodeBitmap(path) } }
    if (photoBitmap != null) {
        Image(
            bitmap = photoBitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(CircleShape),
        )
    } else {
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
}

private fun decodeBitmap(path: String) = try {
    BitmapFactory.decodeFile(path)?.asImageBitmap()
} catch (error: Exception) {
    null
}

fun parseHexColor(hex: String): Color =
    try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (error: IllegalArgumentException) {
        TymedColors.primary
    }

/** Shared by "Add profile" and "Edit profile" — a name field plus a tappable circular photo
 * preview that opens the system photo picker. The picked image is copied into app-private
 * storage (see [ProfilePhotoStore]) as soon as it's picked, not deferred until Save, so the
 * preview reflects the actual saved file. Any photo staged during this dialog session but not
 * ultimately confirmed (replaced by picking again, or the dialog cancelled) is cleaned up rather
 * than left as an orphaned file — [initialPhotoPath] (the profile's already-persisted photo, if
 * editing) is never touched here, only files created *during this session*. */
@Composable
private fun ProfileEditDialog(
    title: String,
    initialName: String,
    initialPhotoPath: String?,
    onConfirm: (name: String, photoPath: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initialName) }
    var photoPath by remember { mutableStateOf(initialPhotoPath) }
    var isSavingPhoto by remember { mutableStateOf(false) }

    fun discardStagedPhoto() {
        photoPath?.takeIf { it != initialPhotoPath }?.let { ProfilePhotoStore.delete(it) }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isSavingPhoto = true
        coroutineScope.launch {
            val saved = withContext(Dispatchers.IO) { ProfilePhotoStore.save(context, uri) }
            if (saved != null) {
                discardStagedPhoto()
                photoPath = saved
            }
            isSavingPhoto = false
        }
    }

    fun launchPicker() = photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    val dismiss = {
        discardStagedPhoto()
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(title) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(TymedSpacing.sm),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(TymedColors.sand)
                        .clickable(enabled = !isSavingPhoto) { launchPicker() },
                    contentAlignment = Alignment.Center,
                ) {
                    val previewBitmap = photoPath?.let { path -> remember(path) { decodeBitmap(path) } }
                    if (previewBitmap != null) {
                        Image(
                            bitmap = previewBitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(Icons.Default.AddAPhoto, contentDescription = "Add photo", tint = TymedColors.textMuted)
                    }
                    if (isSavingPhoto) {
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), color = Color.White)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(TymedSpacing.sm)) {
                    TextButton(onClick = ::launchPicker, enabled = !isSavingPhoto) {
                        Text(if (photoPath != null) "Change photo" else "Add photo")
                    }
                    if (photoPath != null) {
                        TextButton(onClick = {
                            discardStagedPhoto()
                            photoPath = null
                        }) { Text("Remove photo") }
                    }
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") })
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, photoPath) }, enabled = name.isNotBlank() && !isSavingPhoto) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}
