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
 * original design intent.
 *
 * Every text pairing used in the app meets WCAG AA (≥ 4.5:1): [text]/[textMuted] on
 * [background]/[card]/[border], white on [primary], and each accent on its own `*Muted` tint. */
object TymedColors {
    val background = Color(0xFFFAF0DF)
    val card = Color(0xFFFFF9EF)
    val border = Color(0xFFDCC19B)
    /** Edges that must read against [background] itself (≥ 2:1), e.g. the calendar card. */
    val borderStrong = Color(0xFFC9A77A)
    /** A warm sand fill for feature cards that should stand off the page, e.g. the calendar. */
    val sand = Color(0xFFF6E3C3)
    val text = Color(0xFF2B1D12)
    val textMuted = Color(0xFF634A33)
    val primary = Color(0xFFA84A1C)
    val primaryMuted = Color(0xFFFCE8D6)
    val success = Color(0xFF3D6E2F)
    val successMuted = Color(0xFFDFEBCB)
    val warning = Color(0xFF8A5A00)
    val warningMuted = Color(0xFFFBE3AE)
    val danger = Color(0xFFA3301C)
    val dangerMuted = Color(0xFFF6D5CA)

    /** Material's tonal surfaces (navigation bar, dialogs, pickers, switch tracks), stepped from
     * [card] toward [border] so they stay in the warm family instead of Material's lavender. */
    val surfaceContainerLow = Color(0xFFFCF4E6)
    val surfaceContainer = Color(0xFFF5E6CE)
    val surfaceContainerHigh = Color(0xFFF3E3CA)
    val surfaceContainerHighest = Color(0xFFEBD6B7)
}

/** Kept independent of [TymedColors] so the mascot's look can evolve separately from the
 * functional palette. */
object MascotColors {
    val leaf = Color(0xFF6E9A4E)
    val leafLight = Color(0xFF9CC273)
    val leafMid = Color(0xFF83AD5F)
    val leafDark = Color(0xFF4E7A38)
    val vein = Color(0xFFB9D796)
    val stem = Color(0xFF6B5236)
    val flower = Color(0xFFC9A0DC)
    val flowerDeep = Color(0xFFA97CC0)
    val flowerEye = Color(0xFFF4E3F7)
    val pot = Color(0xFFC2622D)
    val potShade = Color(0xFFA84A1C)
    val potRim = Color(0xFFDB8450)
    val face = Color(0xFF2B1D12)
    val shine = Color(0xFFFFFDF8)
    val blush = Color(0xFFEE9A7C)
    val tongue = Color(0xFFE07A6A)
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
    secondary = TymedColors.primary,
    onSecondary = Color.White,
    secondaryContainer = TymedColors.primaryMuted,
    background = TymedColors.background,
    onBackground = TymedColors.text,
    surface = TymedColors.card,
    onSurface = TymedColors.text,
    onSecondaryContainer = TymedColors.text,
    onSurfaceVariant = TymedColors.textMuted,
    surfaceVariant = TymedColors.surfaceContainerHighest,
    surfaceContainerLowest = TymedColors.card,
    surfaceContainerLow = TymedColors.surfaceContainerLow,
    surfaceContainer = TymedColors.surfaceContainer,
    surfaceContainerHigh = TymedColors.surfaceContainerHigh,
    surfaceContainerHighest = TymedColors.surfaceContainerHighest,
    outline = TymedColors.border,
    error = TymedColors.danger,
    onError = Color.White,
)

// No colors baked into these styles: MaterialTheme makes bodyLarge the default text style, and a
// color set here would override every component's content color (e.g. white on filled buttons).
// Body text still comes out as TymedColors.text via the Scaffold's onBackground/onSurface.
private val TymedTypography = Typography(
    bodyLarge = Typography().bodyLarge.copy(fontSize = 16.sp),
    bodyMedium = Typography().bodyMedium.copy(fontSize = 14.sp),
    titleLarge = Typography().titleLarge.copy(fontSize = 22.sp),
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
