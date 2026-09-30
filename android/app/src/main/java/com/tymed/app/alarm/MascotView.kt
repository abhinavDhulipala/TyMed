package com.tymed.app.alarm

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.view.View
import android.view.animation.LinearInterpolator
import com.tymed.app.ui.components.ThymePainter
import kotlin.math.sin

/**
 * Animated "Thyme" for the full-screen alarm screen — the same character as
 * [com.tymed.app.ui.components.Mascot], drawn by the shared [ThymePainter]. [progress] drives the
 * pose continuously: at 0 (alarm just started ringing) Thyme is waving with an open smile, and as
 * progress climbs to 1 (five ringing minutes in) the arm drops, the mouth closes into a frown and
 * the brows knit. The whole mascot sways gently left and right throughout, and the raised arm
 * wiggles in a wave that fades as it lowers.
 */
class MascotView(context: Context) : View(context) {
    /** 0 = just started ringing (waving). 1 = five minutes in (frowning). */
    var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
        }

    private var phase = 0f
    private val swayAnimator = ValueAnimator.ofFloat(0f, (Math.PI * 2).toFloat()).apply {
        duration = 2600
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        swayAnimator.start()
    }

    override fun onDetachedFromWindow() {
        swayAnimator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = minOf(width, height) / 100f
        if (scale <= 0f) return

        canvas.save()
        canvas.translate(width / 2f, height / 2f)
        canvas.rotate(sin(phase) * SWAY_DEGREES)
        canvas.scale(scale, scale)
        canvas.translate(-50f, -50f)

        // Three waves per sway cycle, so the loop stays seamless.
        ThymePainter.draw(canvas, progress, waveDegrees = sin(phase * 3) * WAVE_DEGREES)

        canvas.restore()
    }

    companion object {
        private const val SWAY_DEGREES = 5f
        private const val WAVE_DEGREES = 10f
    }
}
