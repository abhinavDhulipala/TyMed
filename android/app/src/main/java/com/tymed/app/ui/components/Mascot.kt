package com.tymed.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** "Thyme" — a leafy-blob creature with leaf arms, terracotta pot feet and a flowering thyme sprig
 * on its crown; the leafy blob IS the character, not a potted plant with a face bolted on. Static
 * (always waving and smiling) — used in empty states, settings, and the assistant's preparing UI.
 * The same drawing, [ThymePainter], animates on the native alarm screen, see
 * com.tymed.app.alarm.MascotView. */
@Composable
fun Mascot(modifier: Modifier = Modifier, size: Dp = 96.dp) {
    Canvas(modifier = modifier.size(size)) {
        scale(this.size.minDimension / 100f, pivot = Offset.Zero) {
            drawIntoCanvas { ThymePainter.draw(it.nativeCanvas) }
        }
    }
}
