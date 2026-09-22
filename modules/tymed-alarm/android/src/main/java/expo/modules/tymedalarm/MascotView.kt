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
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Procedurally-drawn "Thyme" sprig for the full-screen alarm screen. Pure Canvas (no extra
 * assets) so [progress] can drive it continuously: at 0 (alarm just started ringing) it's a
 * calm, friendly little sprout with a full set of leaves, blinking lazily and breathing deep.
 * As progress climbs toward 1 (five ringing minutes in) leaf pairs shed from the ground up
 * leaving bare branch stubs, the remaining leaves start trembling, and the face works through
 * a whole sequence of "frames" of worry — brows knitting, pupils darting, blink rate climbing,
 * blush draining, a crease forming, and finally the mouth falling open with a sweat drop and
 * tears — instead of one static "sad face" swapped in at the end.
 */
class MascotView(context: Context) : View(context) {
  /** 0 = just started ringing (full leaves, calm). 1 = five minutes in (barren, max distress). */
  var progress: Float = 0f
    set(value) {
      field = value.coerceIn(0f, 1f)
    }

  private var phase = 0f
  private val wobbleAnimator = ValueAnimator.ofFloat(0f, (Math.PI * 2).toFloat()).apply {
    duration = 2200
    repeatCount = ValueAnimator.INFINITE
    interpolator = LinearInterpolator()
    addUpdateListener {
      phase = it.animatedValue as Float
      invalidate()
    }
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    wobbleAnimator.start()
  }

  override fun onDetachedFromWindow() {
    wobbleAnimator.cancel()
    super.onDetachedFromWindow()
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    val scale = minOf(width, height) / 100f
    if (scale <= 0f) return

    // Calm gentle sway grows into a fast, wide shiver as progress rises.
    val swayDeg = sin(phase) * (2f + progress * 2f)
    val jitterFreq = 1f + progress * 6f
    val jitterAmp = progress * progress * 5f * scale
    // Deep slow breathing when calm settles into a shallow, quicker breath under stress.
    val breathFreq = 0.5f + progress * 0.9f
    val breathScale = 1f + sin(phase * breathFreq) * 0.018f * (1f - progress * 0.4f)

    canvas.save()
    canvas.translate(width / 2f + sin(phase * jitterFreq) * jitterAmp, height / 2f)
    canvas.rotate(swayDeg)
    canvas.scale(scale * breathScale, scale * breathScale)
    canvas.translate(-50f, -50f)

    drawPot(canvas)
    drawStemAndLeaves(canvas)
    drawHead(canvas)

    canvas.restore()
  }

  private fun drawPot(canvas: Canvas) {
    canvas.drawPath(potPath, potPaint)
    canvas.drawPath(potRimPath, potRimPaint)
  }

  private fun drawStemAndLeaves(canvas: Canvas) {
    canvas.drawPath(stemPath, stemPaint)

    val shedCount = floor(progress * LEAF_HEIGHTS.size).toInt().coerceIn(0, LEAF_HEIGHTS.size)
    LEAF_HEIGHTS.forEachIndexed { index, y ->
      // Branch stubs always sit under the leaves, so a shed pair reveals bare wood.
      canvas.drawLine(50f, y, 44f, y - 5f, stubPaint)
      canvas.drawLine(50f, y, 56f, y - 5f, stubPaint)

      if (index >= shedCount) {
        val color = LEAF_COLORS[index % LEAF_COLORS.size]
        // Independent, slightly out-of-phase rustle per leaf and per side reads as a light
        // breeze at rest and a nervous tremble once progress climbs.
        val rustleAmp = 2.5f + progress * 9f
        drawLeaf(canvas, y, -1f, color, sin(phase * 1.3f + index) * rustleAmp)
        drawLeaf(canvas, y, 1f, color, sin(phase * 1.3f + index + 1.7f) * rustleAmp)
      }
    }
  }

  private fun drawLeaf(canvas: Canvas, y: Float, side: Float, color: Int, rustleDeg: Float) {
    canvas.save()
    canvas.translate(50f, y)
    canvas.rotate(rustleDeg)
    canvas.translate(-50f, -y)

    leafPaint.color = color
    val path = leafPath(50f, y, side)
    canvas.drawPath(path, leafPaint)

    leafOutlinePaint.color = darken(color)
    canvas.drawPath(path, leafOutlinePaint)

    val dx = LEAF_REACH * side
    veinPaint.color = darken(color)
    canvas.drawLine(50f + dx * 0.15f, y - 2f, 50f + dx * 0.75f, y - 15f, veinPaint)

    canvas.restore()
  }

  private fun drawHead(canvas: Canvas) {
    headPaint.color = lerpColor(LEAF_GREEN, BARE_BROWN, progress)
    canvas.drawCircle(50f, 22f, 16f, headPaint)

    highlightPaint.alpha = ((1f - progress) * 140).roundToInt().coerceIn(0, 255)
    canvas.drawOval(RectF(36f, 8f, 52f, 23f), highlightPaint)

    drawEyes(canvas)

    // nose: a friendly little bump, not a distress signal
    canvas.drawCircle(50f, 24f, 1.1f, nosePaint)

    // blush fades as the mascot pales with worry
    blushPaint.alpha = (0.75f * 255 * (1f - progress * 0.6f)).roundToInt().coerceIn(0, 255)
    canvas.drawOval(RectF(36f, 25f, 44f, 29f), blushPaint)
    canvas.drawOval(RectF(56f, 25f, 64f, 29f), blushPaint)

    drawMouth(canvas)

    // worried brows and the crease between them fade in together
    val browAlpha = (progress * 255).roundToInt().coerceIn(0, 255)
    browPaint.alpha = browAlpha
    creasePaint.alpha = browAlpha
    canvas.drawLine(39f, 12f, 46f, 16f, browPaint)
    canvas.drawLine(61f, 12f, 54f, 16f, browPaint)
    canvas.drawLine(49f, 14f, 49.5f, 18f, creasePaint)
    canvas.drawLine(50.5f, 14f, 50f, 18f, creasePaint)

    // sweat and tears only show up in the final stretch
    val worstAlpha = ((progress - 0.6f) / 0.4f).coerceIn(0f, 1f)
    if (worstAlpha > 0f) {
      sweatPaint.alpha = (worstAlpha * 220).roundToInt().coerceIn(0, 255)
      canvas.drawPath(sweatDropPath, sweatPaint)

      tearPaint.alpha = (worstAlpha * 200).roundToInt().coerceIn(0, 255)
      canvas.save()
      canvas.translate(0f, worstAlpha * 3f)
      canvas.drawPath(leftTearPath, tearPaint)
      canvas.drawPath(rightTearPath, tearPaint)
      canvas.restore()
    }
  }

  private fun drawEyes(canvas: Canvas) {
    // Lazy blink at rest speeds up into a nervous flutter under stress. A sharpened sine spike
    // gives a quick close-and-open rather than a slow sinusoidal droop.
    val blinkFreq = 0.5f + progress * 1.6f
    val blinkSpike = sin(phase * blinkFreq).coerceAtLeast(0f).pow(24)
    val eyeOpen = (1f - blinkSpike).coerceIn(0.08f, 1f)

    // Pupils drift side to side, unsettled, once there's something to worry about.
    val pupilShift = sin(phase * 0.8f) * progress * 1.4f

    for (side in floatArrayOf(-1f, 1f)) {
      val cx = 50f + side * 6f
      val cy = 21f
      val ry = 3.4f * eyeOpen
      canvas.drawOval(RectF(cx - 3.4f, cy - ry, cx + 3.4f, cy + ry), facePaint)
      if (eyeOpen > 0.35f) {
        canvas.drawCircle(cx + 1f + pupilShift, cy - ry * 0.4f, 1f, whitePaint)
      }
    }
  }

  private fun drawMouth(canvas: Canvas) {
    // Smile control point sits below the baseline; a frown pulls it above.
    val controlY = lerp(38f, 30f, progress)
    val mouthCurve = Path().apply {
      moveTo(44f, 31f)
      quadTo(50f, controlY, 56f, 31f)
    }
    val gaspAmount = ((progress - 0.75f) / 0.25f).coerceIn(0f, 1f)

    mouthPaint.alpha = ((1f - gaspAmount) * 255).roundToInt().coerceIn(0, 255)
    canvas.drawPath(mouthCurve, mouthPaint)

    if (gaspAmount > 0f) {
      gaspPaint.alpha = (gaspAmount * 255).roundToInt().coerceIn(0, 255)
      val ry = 2.4f * gaspAmount
      canvas.drawOval(RectF(47f, 30f - ry, 53f, 30f + ry), gaspPaint)
    }
  }

  private fun leafPath(x: Float, y: Float, side: Float): Path {
    // A full, rounded outer bulge tapering to a point, with a tighter inner edge close to the
    // stem — a proper broad leaf silhouette rather than a thin needle, so each one stays
    // legible as its own leaf even when a neighboring pair overlaps it.
    val dx = LEAF_REACH * side
    return Path().apply {
      moveTo(x, y)
      quadTo(x + dx * 0.75f, y - 5f, x + dx, y - 18f)
      quadTo(x + dx * 0.1f, y - 11f, x, y)
      close()
    }
  }

  private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

  private fun lerpColor(from: Int, to: Int, t: Float): Int {
    return Color.rgb(
      lerp(Color.red(from).toFloat(), Color.red(to).toFloat(), t).roundToInt(),
      lerp(Color.green(from).toFloat(), Color.green(to).toFloat(), t).roundToInt(),
      lerp(Color.blue(from).toFloat(), Color.blue(to).toFloat(), t).roundToInt()
    )
  }

  private fun darken(color: Int): Int {
    return Color.rgb(
      (Color.red(color) * 0.7f).roundToInt(),
      (Color.green(color) * 0.7f).roundToInt(),
      (Color.blue(color) * 0.7f).roundToInt()
    )
  }

  companion object {
    // Six pairs spaced widely enough that each one reads as its own leaf rather than
    // blurring into a fern; the outline stroke in drawLeaf() keeps overlaps crisp too.
    private val LEAF_HEIGHTS = floatArrayOf(77f, 69f, 61f, 53f, 45f, 37f)
    private const val LEAF_REACH = 17f

    private val LEAF_GREEN = Color.parseColor("#6B9E52")
    private val LEAF_LIGHT = Color.parseColor("#9AC77E")
    private val LEAF_DARK = Color.parseColor("#4A7A3B")
    private val LEAF_COLORS = intArrayOf(LEAF_GREEN, LEAF_LIGHT, LEAF_DARK)
    private val BARE_BROWN = Color.parseColor("#B08968")
    private val STEM_BROWN = Color.parseColor("#8A6A4A")
    private val POT_COLOR = Color.parseColor("#D97742")
    private val POT_RIM_COLOR = Color.parseColor("#E89760")
    private val FACE_COLOR = Color.parseColor("#3B2E22")
    private val BLUSH_COLOR = Color.parseColor("#F0A488")
    private val SWEAT_COLOR = Color.parseColor("#8FC7E8")
    private val TEAR_COLOR = Color.parseColor("#A9D8F0")

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
    private val stemPath = Path().apply {
      moveTo(50f, 78f)
      quadTo(44f, 58f, 50f, 38f)
    }
    private val sweatDropPath = Path().apply {
      moveTo(63f, 10f)
      quadTo(68f, 16f, 63f, 20f)
      quadTo(58f, 16f, 63f, 10f)
      close()
    }
    private val leftTearPath = Path().apply {
      moveTo(44f, 26f)
      quadTo(46.5f, 31f, 44f, 34f)
      quadTo(41.5f, 31f, 44f, 26f)
      close()
    }
    private val rightTearPath = Path().apply {
      moveTo(56f, 26f)
      quadTo(58.5f, 31f, 56f, 34f)
      quadTo(53.5f, 31f, 56f, 26f)
      close()
    }
  }

  private val potPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = POT_COLOR }
  private val potRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = POT_RIM_COLOR }
  private val stemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = STEM_BROWN
    style = Paint.Style.STROKE
    strokeWidth = 5f
    strokeCap = Paint.Cap.ROUND
  }
  private val stubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = STEM_BROWN
    style = Paint.Style.STROKE
    strokeWidth = 2.6f
    strokeCap = Paint.Cap.ROUND
  }
  private val leafPaint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val leafOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeWidth = 1.3f
    strokeJoin = Paint.Join.ROUND
  }
  private val veinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeWidth = 1f
    strokeCap = Paint.Cap.ROUND
    alpha = 140
  }
  private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LEAF_LIGHT }
  private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = FACE_COLOR }
  private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
  private val nosePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = FACE_COLOR
    alpha = 120
  }
  private val blushPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BLUSH_COLOR }
  private val mouthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = FACE_COLOR
    style = Paint.Style.STROKE
    strokeWidth = 2.6f
    strokeCap = Paint.Cap.ROUND
  }
  private val gaspPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = FACE_COLOR }
  private val browPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = FACE_COLOR
    style = Paint.Style.STROKE
    strokeWidth = 2.4f
    strokeCap = Paint.Cap.ROUND
  }
  private val creasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = FACE_COLOR
    style = Paint.Style.STROKE
    strokeWidth = 1.2f
    strokeCap = Paint.Cap.ROUND
  }
  private val sweatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SWEAT_COLOR }
  private val tearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TEAR_COLOR }
}
