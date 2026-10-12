package com.tymed.app.ui.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tymed.app.ui.components.Mascot
import com.tymed.app.ui.theme.TymedColors
import com.tymed.app.ui.theme.TymedSpacing

/** Shown once, only on a brand-new install with zero profiles — every pre-existing install
 * already has a "Me" profile seeded by MIGRATION_7_8, so this path is fresh-install only. */
@Composable
fun FirstProfileScreen(onCreate: (String) -> Unit, modifier: Modifier = Modifier) {
    var name by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(TymedSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(TymedSpacing.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Mascot()
        Text("Welcome to TyMed", fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Text(
            "Who's this for? You can add more people later from Settings.",
            color = TymedColors.textMuted,
        )
        OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") })
        Button(
            onClick = { onCreate(name) },
            enabled = name.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = TymedColors.primary),
        ) { Text("Get started") }
    }
}
