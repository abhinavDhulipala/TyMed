package expo.modules.tymedalarm

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Procedurally-drawn "Thyme" sprig for the full-screen alarm screen — the same friendly
 * leafy-blob character as [src/components/Mascot.tsx], not a separate design. Pure Canvas
 * (no extra assets) so [progress] can drive it continuously: at 0 (alarm just started
 * ringing) it's smiling, and as progress climbs to 1 (five ringing minutes in) the mouth
 * eases into a frown. The whole mascot sways gently left and right throughout.
 */
class MascotView(context: Context) : View(context) {
  /** 0 = just started ringing (smiling). 1 = five minutes in (frowning). */
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

    val swayDeg = sin(phase) * SWAY_DEGREES

    canvas.save()
    canvas.translate(width / 2f, height / 2f)
    canvas.rotate(swayDeg)
    canvas.scale(scale, scale)
    canvas.translate(-50f, -50f)

    drawPotFeet(canvas)
    drawLeafArms(canvas)
    drawBody(canvas)
    drawFace(canvas)

    canvas.restore()
  }

  private fun drawPotFeet(canvas: Canvas) {
    canvas.drawPath(potPath, potPaint)
    canvas.drawPath(potRimPath, potRimPaint)
  }

  private fun drawLeafArms(canvas: Canvas) {
    canvas.drawPath(leftArmPath, leafPaint)
    canvas.drawPath(rightArmPath, leafPaint)
  }

  private fun drawBody(canvas: Canvas) {
    canvas.drawOval(RectF(21f, 19f, 79f, 73f), leafPaint)
    canvas.drawOval(RectF(14f, 26f, 44f, 52f), leafLightPaint)
    canvas.drawOval(RectF(56f, 26f, 86f, 52f), leafDarkPaint)
    canvas.drawOval(RectF(36f, 11f, 64f, 35f), leafLightPaint)
  }

  private fun drawFace(canvas: Canvas) {
    canvas.drawCircle(40f, 48f, 4.6f, facePaint)
    canvas.drawCircle(60f, 48f, 4.6f, facePaint)
    canvas.drawCircle(41.6f, 46.4f, 1.4f, whitePaint)
    canvas.drawCircle(61.6f, 46.4f, 1.4f, whitePaint)

    canvas.drawOval(RectF(28f, 52f, 38f, 58f), blushPaint)
    canvas.drawOval(RectF(62f, 52f, 72f, 58f), blushPaint)

    drawMouth(canvas)
  }

  private fun drawMouth(canvas: Canvas) {
    // Smile control point sits below the baseline; a frown pulls it the same distance above.
    val controlY = lerp(63.5f, 50.5f, progress)
    val mouthPath = Path().apply {
      moveTo(43f, 57f)
      quadTo(50f, controlY, 57f, 57f)
    }
    canvas.drawPath(mouthPath, mouthPaint)
  }

  private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

  companion object {
    private const val SWAY_DEGREES = 5f

    private val LEAF_COLOR = Color.parseColor("#6B9E52")
    private val LEAF_LIGHT_COLOR = Color.parseColor("#9AC77E")
    private val LEAF_DARK_COLOR = Color.parseColor("#4A7A3B")
    private val POT_COLOR = Color.parseColor("#D97742")
    private val POT_RIM_COLOR = Color.parseColor("#E89760")
    private val FACE_COLOR = Color.parseColor("#3B2E22")
    private val BLUSH_COLOR = Color.parseColor("#F0A488")

    private val potPath = Path().apply {
      moveTo(32f, 78f)
      lineTo(68f, 78f)
      lineTo(64f, 94f)
      quadTo(50f, 98f, 36f, 94f)
      close()
    }
    private val potRimPath = Path().apply {
      moveTo(30f, 78f)
      lineTo(70f, 78f)
      lineTo(70f, 84f)
      lineTo(30f, 84f)
      close()
    }
    private val leftArmPath = Path().apply {
      moveTo(19f, 54f)
      quadTo(6f, 49f, 9f, 37f)
      quadTo(21f, 41f, 24f, 54f)
      close()
    }
    private val rightArmPath = Path().apply {
      moveTo(81f, 54f)
      quadTo(94f, 49f, 91f, 37f)
      quadTo(79f, 41f, 76f, 54f)
      close()
    }
  }

  private val potPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = POT_COLOR }
  private val potRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = POT_RIM_COLOR }
  private val leafPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LEAF_COLOR }
  private val leafLightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LEAF_LIGHT_COLOR }
  private val leafDarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LEAF_DARK_COLOR }
  private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = FACE_COLOR }
  private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
  private val blushPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = BLUSH_COLOR
    alpha = (0.75f * 255).roundToInt()
  }
  private val mouthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = FACE_COLOR
    style = Paint.Style.STROKE
    strokeWidth = 2.6f
    strokeCap = Paint.Cap.ROUND
  }
}
