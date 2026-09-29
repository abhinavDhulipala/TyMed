package com.tymed.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tymed.app.ui.theme.MascotColors

/** "Thyme" — a leafy-blob creature with two small waving leaf-arms and a terracotta pot as feet;
 * the leafy blob IS the character, not a potted plant with a face bolted on. Static (always
 * smiling) — used in empty states, settings, and the assistant's preparing UI. The same visual
 * design animates (sway + smile-to-frown) on the native alarm screen, see
 * com.tymed.app.alarm.MascotView. */
@Composable
fun Mascot(modifier: Modifier = Modifier, size: Dp = 96.dp) {
    Canvas(modifier = modifier.size(size)) {
        val scale = kotlin.math.min(size.toPx(), size.toPx()) / 100f

        fun px(x: Float, y: Float) = Offset(x * scale, y * scale)

        val potPath = Path().apply {
            moveTo(32f * scale, 78f * scale)
            lineTo(68f * scale, 78f * scale)
            lineTo(64f * scale, 94f * scale)
            quadraticTo(50f * scale, 98f * scale, 36f * scale, 94f * scale)
            close()
        }
        val potRimPath = Path().apply {
            moveTo(30f * scale, 78f * scale)
            lineTo(70f * scale, 78f * scale)
            lineTo(70f * scale, 84f * scale)
            lineTo(30f * scale, 84f * scale)
            close()
        }
        val leftArmPath = Path().apply {
            moveTo(19f * scale, 54f * scale)
            quadraticTo(6f * scale, 49f * scale, 9f * scale, 37f * scale)
            quadraticTo(21f * scale, 41f * scale, 24f * scale, 54f * scale)
            close()
        }
        val rightArmPath = Path().apply {
            moveTo(81f * scale, 54f * scale)
            quadraticTo(94f * scale, 49f * scale, 91f * scale, 37f * scale)
            quadraticTo(79f * scale, 41f * scale, 76f * scale, 54f * scale)
            close()
        }
        val mouthPath = Path().apply {
            moveTo(43f * scale, 57f * scale)
            quadraticTo(50f * scale, 63.5f * scale, 57f * scale, 57f * scale)
        }

        drawPath(potPath, color = MascotColors.pot)
        drawPath(potRimPath, color = MascotColors.potRim)
        drawPath(leftArmPath, color = MascotColors.leaf)
        drawPath(rightArmPath, color = MascotColors.leaf)

        drawOval(color = MascotColors.leaf, topLeft = px(21f, 19f), size = Size(58f * scale, 54f * scale))
        drawOval(color = MascotColors.leafLight, topLeft = px(14f, 26f), size = Size(30f * scale, 26f * scale))
        drawOval(color = MascotColors.leafDark, topLeft = px(56f, 26f), size = Size(30f * scale, 26f * scale))
        drawOval(color = MascotColors.leafLight, topLeft = px(36f, 11f), size = Size(28f * scale, 24f * scale))

        drawCircle(color = MascotColors.face, radius = 4.6f * scale, center = px(40f, 48f))
        drawCircle(color = MascotColors.face, radius = 4.6f * scale, center = px(60f, 48f))
        drawCircle(color = androidx.compose.ui.graphics.Color.White, radius = 1.4f * scale, center = px(41.6f, 46.4f))
        drawCircle(color = androidx.compose.ui.graphics.Color.White, radius = 1.4f * scale, center = px(61.6f, 46.4f))

        drawOval(
            color = MascotColors.blush.copy(alpha = 0.75f),
            topLeft = px(28f, 52f),
            size = Size(10f * scale, 6f * scale),
        )
        drawOval(
            color = MascotColors.blush.copy(alpha = 0.75f),
            topLeft = px(62f, 52f),
            size = Size(10f * scale, 6f * scale),
        )

        drawPath(
            mouthPath,
            color = MascotColors.face,
            style = Stroke(width = 2.6f * scale, cap = androidx.compose.ui.graphics.StrokeCap.Round),
        )
    }
}
