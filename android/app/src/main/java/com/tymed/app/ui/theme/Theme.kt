package com.tymed.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A warm, cheerful, slightly vintage take on a light Material palette — solid/opaque fills,
 * like an old apothecary label. Deliberately light-only (no dark variant), matching the app's
 * original design intent. */
object TymedColors {
    val background = Color(0xFFF6EFE2)
    val card = Color(0xFFFFFBF3)
    val border = Color(0xFFE6D8C0)
    val text = Color(0xFF3B2E22)
    val textMuted = Color(0xFF8A7860)
    val primary = Color(0xFFD97742)
    val primaryMuted = Color(0xFFF5DFC8)
    val success = Color(0xFF5A8C4E)
    val successMuted = Color(0xFFE3EDD8)
    val warning = Color(0xFFCC9A2E)
    val warningMuted = Color(0xFFF6E7C0)
    val danger = Color(0xFFB84B37)
    val dangerMuted = Color(0xFFF3DBD2)
}

/** Kept independent of [TymedColors] so the mascot's look can evolve separately from the
 * functional palette. */
object MascotColors {
    val leaf = Color(0xFF6B9E52)
    val leafLight = Color(0xFF9AC77E)
    val leafDark = Color(0xFF4A7A3B)
    val pot = Color(0xFFD97742)
    val potRim = Color(0xFFE89760)
    val face = Color(0xFF3B2E22)
    val blush = Color(0xFFF0A488)
}

object TymedSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}

object TymedRadii {
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
}

private val TymedColorScheme = lightColorScheme(
    primary = TymedColors.primary,
    onPrimary = Color.White,
    secondaryContainer = TymedColors.primaryMuted,
    background = TymedColors.background,
    onBackground = TymedColors.text,
    surface = TymedColors.card,
    onSurface = TymedColors.text,
    outline = TymedColors.border,
    error = TymedColors.danger,
)

private val TymedTypography = Typography(
    bodyLarge = Typography().bodyLarge.copy(fontSize = 16.sp, color = TymedColors.text),
    bodyMedium = Typography().bodyMedium.copy(fontSize = 14.sp, color = TymedColors.text),
    titleLarge = Typography().titleLarge.copy(fontSize = 22.sp, color = TymedColors.text),
)

@Composable
fun TymedTheme(content: @Composable () -> Unit) {
    // The palette is intentionally light-only (see TymedColors doc) regardless of system theme.
    MaterialTheme(
        colorScheme = TymedColorScheme,
        typography = TymedTypography,
        content = content,
    )
}
