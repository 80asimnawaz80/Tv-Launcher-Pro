package com.analogtv.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 3D analog clock (metal bezel, shisha ka dial, hands ka shadow) - saath digits.
 * style: 0 Classic (1-12), 1 Minimal (12,3,6,9), 2 Roman, 3 Dots + digits, 4 Neon.
 */
class AnalogClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var accent: Int = 0xFF2D9CFF.toInt()
        set(v) {
            field = v
            invalidate()
        }

    var style: Int = 0
        set(v) {
            field = v
            invalidate()
        }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val oval = RectF()
    private val circle = Path()
    private val romans = arrayOf("XII", "I", "II", "III", "IIII", "V", "VI", "VII", "VIII", "IX", "X", "XI")

    private val tick = object : Runnable {
        override fun run() {
            invalidate()
            postDelayed(this, 1000L - System.currentTimeMillis() % 1000L)
        }
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null) // shadow / glow ke liye
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post(tick)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    private fun px(cx: Float, deg: Float, len: Float): Float = cx + len * sin(deg * PI / 180.0).toFloat()
    private fun py(cy: Float, deg: Float, len: Float): Float = cy - len * cos(deg * PI / 180.0).toFloat()

    private fun label(c: Canvas, txt: String, x: Float, y: Float, size: Float, color: Int) {
        t.textSize = size
        t.color = color
        val fm = t.fontMetrics
        c.drawText(txt, x, y - (fm.ascent + fm.descent) / 2f, t)
    }

    private fun hand(c: Canvas, cx: Float, cy: Float, deg: Float, len: Float, tail: Float, w: Float, color: Int) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = w
        p.color = color
        c.drawLine(px(cx, deg + 180f, tail), py(cy, deg + 180f, tail), px(cx, deg, len), py(cy, deg, len), p)
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f - 5f
        if (r <= 8f) return

        val a = accent
        val neon = style == 4
        val cal = Calendar.getInstance()
        val h = cal.get(Calendar.HOUR)
        val m = cal.get(Calendar.MINUTE)
        val s = cal.get(Calendar.SECOND)
        val hourA = (h + m / 60f) * 30f
        val minA = (m + s / 60f) * 6f
        val secA = s * 6f

        p.clearShadowLayer()
        t.clearShadowLayer()
        p.shader = null

        // ---- bezel (metal ring, sweep gradient) + bahar ka shadow
        p.style = Paint.Style.FILL
        p.setShadowLayer(r * 0.10f, 0f, r * 0.07f, Color.argb(150, 0, 0, 0))
        p.shader = SweepGradient(
            cx, cy,
            intArrayOf(
                Color.WHITE, ColorUtils.blendARGB(a, Color.WHITE, 0.4f), ColorUtils.blendARGB(a, Color.BLACK, 0.7f),
                Color.WHITE, ColorUtils.blendARGB(a, Color.BLACK, 0.6f), ColorUtils.blendARGB(a, Color.WHITE, 0.4f), Color.WHITE
            ),
            null
        )
        c.drawCircle(cx, cy, r, p)
        p.shader = null
        p.clearShadowLayer()

        // ---- dial (shisha jaisa gehra, centre thoda roshan)
        val fr = r * 0.88f
        p.shader = RadialGradient(
            cx - fr * 0.25f, cy - fr * 0.30f, fr * 1.25f,
            ColorUtils.blendARGB(a, Color.BLACK, 0.50f), ColorUtils.blendARGB(a, Color.BLACK, 0.90f),
            Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, fr, p)
        p.shader = null

        // andar ki patli ring
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.018f
        p.color = if (neon) a else Color.argb(110, 255, 255, 255)
        if (neon) p.setShadowLayer(r * 0.10f, 0f, 0f, a)
        c.drawCircle(cx, cy, fr * 0.97f, p)
        p.clearShadowLayer()

        // ---- digits / ticks
        t.typeface = Typeface.DEFAULT_BOLD
        when (style) {
            0 -> {
                tickMarks(c, cx, cy, fr, Color.WHITE, 60)
                for (i in 1..12) label(c, i.toString(), px(cx, i * 30f, fr * 0.72f), py(cy, i * 30f, fr * 0.72f), fr * 0.20f, Color.WHITE)
            }
            1 -> {
                t.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                for (i in intArrayOf(12, 3, 6, 9)) {
                    label(c, i.toString(), px(cx, i * 30f, fr * 0.70f), py(cy, i * 30f, fr * 0.70f), fr * 0.26f, Color.WHITE)
                }
            }
            2 -> {
                t.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                tickMarks(c, cx, cy, fr, Color.argb(200, 255, 255, 255), 12)
                for (i in 0 until 12) {
                    val big = if (i == 0 || i == 3 || i == 6 || i == 9) 0.21f else 0.17f
                    label(c, romans[i], px(cx, i * 30f, fr * 0.70f), py(cy, i * 30f, fr * 0.70f), fr * big, Color.WHITE)
                }
            }
            3 -> {
                p.style = Paint.Style.FILL
                for (i in 0 until 12) {
                    val q = i % 3 == 0
                    p.color = if (q) a else Color.WHITE
                    c.drawCircle(px(cx, i * 30f, fr * 0.88f), py(cy, i * 30f, fr * 0.88f), if (q) fr * 0.055f else fr * 0.03f, p)
                }
                for (i in 1..12) label(c, i.toString(), px(cx, i * 30f, fr * 0.66f), py(cy, i * 30f, fr * 0.66f), fr * 0.17f, Color.WHITE)
            }
            else -> {
                tickMarks(c, cx, cy, fr, a, 12)
                t.setShadowLayer(fr * 0.10f, 0f, 0f, a)
                for (i in 1..12) label(c, i.toString(), px(cx, i * 30f, fr * 0.70f), py(cy, i * 30f, fr * 0.70f), fr * 0.20f, Color.WHITE)
                t.clearShadowLayer()
            }
        }

        // ---- hands (shadow ke saath = 3D)
        val shadow = Color.argb(170, 0, 0, 0)
        val thin = style == 1
        val hc = if (neon) a else Color.WHITE
        p.setShadowLayer(r * 0.05f, r * 0.025f, r * 0.045f, shadow)
        hand(c, cx, cy, hourA, fr * 0.46f, fr * 0.10f, if (thin) fr * 0.04f else fr * 0.075f, hc)
        hand(c, cx, cy, minA, fr * 0.68f, fr * 0.12f, if (thin) fr * 0.028f else fr * 0.05f, hc)
        hand(c, cx, cy, secA, fr * 0.78f, fr * 0.18f, fr * 0.022f, a)

        // centre cap (metal)
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx - fr * 0.02f, cy - fr * 0.02f, fr * 0.09f, Color.WHITE, a, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, fr * 0.065f, p)
        p.shader = null
        p.clearShadowLayer()

        // ---- shisha ki chamak (upar ki jhalak)
        c.save()
        circle.reset()
        circle.addCircle(cx, cy, fr, Path.Direction.CW)
        c.clipPath(circle)
        oval.set(cx - fr * 0.85f, cy - fr * 1.05f, cx + fr * 0.85f, cy - fr * 0.05f)
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, cy - fr, 0f, cy - fr * 0.05f, Color.argb(95, 255, 255, 255), Color.argb(6, 255, 255, 255), Shader.TileMode.CLAMP)
        c.drawOval(oval, p)
        p.shader = null
        c.restore()
    }

    private fun tickMarks(c: Canvas, cx: Float, cy: Float, fr: Float, color: Int, count: Int) {
        p.style = Paint.Style.STROKE
        p.color = color
        for (i in 0 until count) {
            val deg = i * (360f / count)
            val major = count == 12 || i % 5 == 0
            p.strokeWidth = if (major) fr * 0.030f else fr * 0.014f
            val inner = if (major) fr * 0.88f else fr * 0.92f
            c.drawLine(px(cx, deg, fr * 0.97f), py(cy, deg, fr * 0.97f), px(cx, deg, inner), py(cy, deg, inner), p)
        }
    }
}
