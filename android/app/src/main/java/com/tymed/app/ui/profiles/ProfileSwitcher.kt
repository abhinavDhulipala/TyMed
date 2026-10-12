package com.tymed.app.ui.profiles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tymed.app.data.entity.Profile

/** Persistent top bar across every tab, showing whoever's active; tapping it opens a picker to
 * switch to another profile or jump to [ProfilesScreen] to add/rename/delete one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSwitcherBar(
    profiles: List<Profile>,
    activeProfile: Profile?,
    onSwitch: (Long) -> Unit,
    onManage: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Box {
                Row(
                    modifier = Modifier
                        .clickable { expanded = true }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (activeProfile != null) {
                        ProfileAvatar(activeProfile, size = 28.dp)
                        Text(activeProfile.name, modifier = Modifier.padding(start = 8.dp))
                    } else {
                        Text("TyMed")
                    }
                }

                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    profiles.forEach { profile ->
                        DropdownMenuItem(
                            text = { Text(profile.name) },
                            leadingIcon = { ProfileAvatar(profile, size = 24.dp) },
                            onClick = {
                                expanded = false
                                onSwitch(profile.id)
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Manage profiles") },
                        onClick = {
                            expanded = false
                            onManage()
                        },
                    )
                }
            }
        },
    )
}
