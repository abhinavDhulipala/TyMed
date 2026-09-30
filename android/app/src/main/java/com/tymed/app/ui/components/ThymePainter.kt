package com.tymed.app.ui.components

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.tymed.app.ui.theme.MascotColors
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws "Thyme" — the leafy-blob mascot with leaf arms, terracotta pot feet and a flowering thyme
 * sprig on its crown — onto a 100×100 canvas. The single drawing shared by the static
 * [Mascot] composable and the animated [com.tymed.app.alarm.MascotView], so the two can't drift.
 *
 * [progress] morphs between the mascot's two poses: at 0 it's waving with an open smile, at 1
 * (five ringing minutes in) the arm has dropped, the mouth has closed into a frown and the brows
 * have knitted. Every pose-dependent shape is interpolated, so any value in between is valid.
 * [waveDegrees] rotates the raised arm about its shoulder (the alarm screen wiggles it); it fades
 * out with the arm as [progress] climbs.
 *
 * Not thread-safe (reuses its paints and paths) — only call it from the UI thread.
 */
object ThymePainter {
    fun draw(canvas: Canvas, progress: Float = 0f, waveDegrees: Float = 0f) {
        val t = progress.coerceIn(0f, 1f)
        canvas.save()
        // Leaves headroom above the crown for the sprig's flower.
        canvas.translate(4f, 7.8f)
        canvas.scale(0.92f, 0.92f)

        oval(canvas, 50f, 95.5f, 20f, 2.6f, MascotColors.face, alpha = 0.12f)
        drawPot(canvas)
        drawArms(canvas, 1f - t, waveDegrees)
        drawSprig(canvas)
        drawBody(canvas)
        drawFace(canvas, t)

        canvas.restore()
    }

    private fun drawPot(canvas: Canvas) {
        fill(canvas, MascotColors.pot) {
            moveTo(32f, 78f); lineTo(68f, 78f); lineTo(64f, 94f); quadTo(50f, 98f, 36f, 94f); close()
        }
        fill(canvas, MascotColors.potShade) {
            moveTo(34.4f, 88f); lineTo(65.6f, 88f); lineTo(64f, 94f); quadTo(50f, 98f, 36f, 94f); close()
        }
        paint.color = MascotColors.potRim.toArgb()
        rect.set(30f, 77f, 70f, 84f)
        canvas.drawRoundRect(rect, 2.5f, 2.5f, paint)
    }

    /** [raise]: 1 = right arm up mid-wave, 0 = resting at its side. */
    private fun drawArms(canvas: Canvas, raise: Float, waveDegrees: Float) {
        fill(canvas, MascotColors.leaf) {
            moveTo(19f, 54f); quadTo(6f, 49f, 9f, 37f); quadTo(21f, 41f, 24f, 54f); close()
        }
        line(canvas, MascotColors.vein, 1.1f) { moveTo(21.5f, 52f); quadTo(15f, 45f, 10.5f, 39f) }

        fun r(rest: Float, raised: Float) = lerp(rest, raised, raise)
        canvas.save()
        canvas.rotate(waveDegrees * raise, r(78.5f, 78.5f), r(54f, 49f))
        fill(canvas, MascotColors.leafDark) {
            moveTo(r(81f, 80f), r(54f, 50f))
            quadTo(r(94f, 96f), r(49f, 44f), r(91f, 96f), r(37f, 29f))
            quadTo(r(79f, 82f), r(41f, 32f), r(76f, 77f), r(54f, 48f))
            close()
        }
        line(canvas, MascotColors.leafMid, 1.1f) {
            moveTo(r(78.5f, 79f), r(52f, 48f))
            quadTo(r(85f, 88f), r(45f, 40f), r(89.5f, 95f), r(39f, 31f))
        }
        canvas.restore()
    }

    private fun drawSprig(canvas: Canvas) {
        line(canvas, MascotColors.stem, 1.9f) { moveTo(49f, 13f); quadTo(50f, 5f, 54f, 0f) }
        oval(canvas, 46.4f, 7.5f, 3.6f, 1.9f, MascotColors.leafDark, degrees = -35f)
        oval(canvas, 53.4f, 6.2f, 3.6f, 1.9f, MascotColors.leaf, degrees = 30f)
        oval(canvas, 49.8f, 2f, 3f, 1.6f, MascotColors.leafDark, degrees = -55f)
        oval(canvas, 56.6f, 1f, 3f, 1.6f, MascotColors.leaf, degrees = 15f)
        // A five-petal thyme blossom at the tip.
        for (k in 0 until 5) {
            val a = Math.toRadians(k * 72.0 - 90.0)
            paint.color = (if (k % 2 == 0) MascotColors.flower else MascotColors.flowerDeep).toArgb()
            canvas.drawCircle(54.6f + 2.4f * cos(a).toFloat(), -3.5f + 2.4f * sin(a).toFloat(), 1.9f, paint)
        }
        paint.color = MascotColors.flowerEye.toArgb()
        canvas.drawCircle(54.6f, -3.5f, 1.3f, paint)
    }

    /** Four overlapping leafy lobes, lit from the top left. */
    private fun drawBody(canvas: Canvas) {
        oval(canvas, 50f, 46f, 29f, 27f, MascotColors.leaf)
        oval(canvas, 71f, 39f, 15f, 13f, MascotColors.leafDark)
        oval(canvas, 29f, 39f, 15f, 13f, MascotColors.leafMid)
        oval(canvas, 50f, 23f, 14f, 12f, MascotColors.leafLight)
        oval(canvas, 44f, 19.5f, 5.5f, 3.2f, MascotColors.vein, degrees = -25f, alpha = 0.9f)
        oval(canvas, 25f, 35f, 4.5f, 2.6f, MascotColors.leafLight, degrees = -35f, alpha = 0.9f)
    }

    private fun drawFace(canvas: Canvas, t: Float) {
        for (x in floatArrayOf(40f, 60f)) {
            paint.color = MascotColors.face.toArgb()
            canvas.drawCircle(x, 48f, 4.6f, paint)
            paint.color = MascotColors.shine.toArgb()
            canvas.drawCircle(x + 1.7f, 46.3f, 1.6f, paint)
            canvas.drawCircle(x - 1.4f, 50f, 0.7f, paint)
        }

        // Brows start relaxed and knit (inner ends up) as the alarm runs on.
        val k = t * 2.2f
        line(canvas, MascotColors.face, 1.9f) { moveTo(36f, 40.5f + k * 0.2f); quadTo(40f, 38.5f - k * 0.3f, 44f, 40f - k) }
        line(canvas, MascotColors.face, 1.9f) { moveTo(56f, 40f - k); quadTo(60f, 38.5f - k * 0.3f, 64f, 40.5f + k * 0.2f) }

        oval(canvas, 33f, 55f, 5f, 3f, MascotColors.blush, alpha = 0.85f)
        oval(canvas, 67f, 55f, 5f, 3f, MascotColors.blush, alpha = 0.85f)

        drawMouth(canvas, t)
    }

    /**
     * The mouth is the region between two curves sharing their corners: open wide it's a grin,
     * and as both control points slide up to the same point it closes into a frown line.
     */
    private fun drawMouth(canvas: Canvas, t: Float) {
        val upper = lerp(56f, 50.5f, t)
        val lower = lerp(66.5f, 50.5f, t)
        path.reset()
        path.moveTo(43f, 57f)
        path.quadTo(50f, upper, 57f, 57f)
        path.quadTo(50f, lower, 43f, 57f)
        path.close()

        paint.color = MascotColors.face.toArgb()
        canvas.drawPath(path, paint)
        stroke.color = MascotColors.face.toArgb()
        stroke.strokeWidth = 2.6f
        canvas.drawPath(path, stroke)

        val openness = 1f - t
        val tongueAlpha = ((openness - 0.45f) / 0.55f).coerceIn(0f, 1f)
        if (tongueAlpha > 0f) {
            canvas.save()
            canvas.clipPath(path)
            val cy = 28.5f + 0.5f * lower - 1.4f // just above the lower lip's lowest point
            oval(canvas, 50f, cy, 3.2f * openness, 1.6f * openness, MascotColors.tongue, alpha = tongueAlpha)
            canvas.restore()
        }
    }

    // --- drawing helpers -------------------------------------------------------------------------

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val rect = RectF()

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun oval(
        canvas: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, color: Color,
        degrees: Float = 0f, alpha: Float = 1f,
    ) {
        paint.color = color.copy(alpha = alpha).toArgb()
        rect.set(cx - rx, cy - ry, cx + rx, cy + ry)
        if (degrees == 0f) {
            canvas.drawOval(rect, paint)
        } else {
            canvas.save()
            canvas.rotate(degrees, cx, cy)
            canvas.drawOval(rect, paint)
            canvas.restore()
        }
    }

    private inline fun fill(canvas: Canvas, color: Color, build: Path.() -> Unit) {
        path.reset()
        path.build()
        paint.color = color.toArgb()
        canvas.drawPath(path, paint)
    }

    private inline fun line(canvas: Canvas, color: Color, width: Float, build: Path.() -> Unit) {
        path.reset()
        path.build()
        stroke.color = color.toArgb()
        stroke.strokeWidth = width
        canvas.drawPath(path, stroke)
    }
}
