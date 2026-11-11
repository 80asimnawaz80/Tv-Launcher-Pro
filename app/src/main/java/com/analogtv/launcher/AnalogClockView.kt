package com.analogtv.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Analog clock. style: 0 Classic, 1 Minimal, 2 Roman, 3 Dots, 4 Neon.
 * accent = seconds hand / highlights ka rang.
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
    private val roman = arrayOf("XII", "I", "II", "III", "IIII", "V", "VI", "VII", "VIII", "IX", "X", "XI")

    private val tick = object : Runnable {
        override fun run() {
            invalidate()
            postDelayed(this, 1000L - System.currentTimeMillis() % 1000L)
        }
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null) // neon glow (shadow layer) ke liye
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post(tick)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    private fun pt(cx: Float, cy: Float, deg: Float, len: Float): Pair<Float, Float> {
        val a = deg * PI / 180.0
        return Pair(cx + len * sin(a).toFloat(), cy - len * cos(a).toFloat())
    }

    private fun hand(c: Canvas, cx: Float, cy: Float, deg: Float, len: Float, w: Float, color: Int) {
        val e = pt(cx, cy, deg, len)
        p.style = Paint.Style.STROKE
        p.strokeWidth = w
        p.color = color
        c.drawLine(cx, cy, e.first, e.second, p)
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f - 6f
        if (r <= 0f) return

        val cal = Calendar.getInstance()
        val h = cal.get(Calendar.HOUR)
        val m = cal.get(Calendar.MINUTE)
        val s = cal.get(Calendar.SECOND)
        val hourA = (h + m / 60f) * 30f
        val minA = (m + s / 60f) * 6f
        val secA = s * 6f

        val neon = style == 4
        p.clearShadowLayer()

        // face
        p.style = Paint.Style.FILL
        p.color = 0x33000000
        c.drawCircle(cx, cy, r, p)

        // ring
        p.style = Paint.Style.STROKE
        p.strokeWidth = if (style == 1) r * 0.02f else r * 0.035f
        p.color = if (neon) accent else 0xAAFFFFFF.toInt()
        if (neon) p.setShadowLayer(r * 0.12f, 0f, 0f, accent)
        c.drawCircle(cx, cy, r, p)
        p.clearShadowLayer()

        when (style) {
            0, 4 -> {
                for (i in 0 until 12) {
                    val major = i % 3 == 0
                    val a = pt(cx, cy, i * 30f, r * 0.90f)
                    val b = pt(cx, cy, i * 30f, r * (if (major) 0.76f else 0.83f))
                    p.strokeWidth = if (major) r * 0.045f else r * 0.025f
                    p.color = if (neon && major) accent else Color.WHITE
                    c.drawLine(a.first, a.second, b.first, b.second, p)
                }
            }
            1 -> {
                for (i in 0 until 4) {
                    val a = pt(cx, cy, i * 90f, r * 0.90f)
                    val b = pt(cx, cy, i * 90f, r * 0.80f)
                    p.strokeWidth = r * 0.02f
                    p.color = 0xCCFFFFFF.toInt()
                    c.drawLine(a.first, a.second, b.first, b.second, p)
                }
            }
            2 -> {
                p.style = Paint.Style.FILL
                p.color = Color.WHITE
                p.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                p.textSize = r * 0.20f
                p.textAlign = Paint.Align.CENTER
                for (i in 0 until 12) {
                    val q = pt(cx, cy, i * 30f, r * 0.76f)
                    c.drawText(roman[i], q.first, q.second + p.textSize * 0.35f, p)
                }
                p.typeface = Typeface.DEFAULT
            }
            3 -> {
                p.style = Paint.Style.FILL
                for (i in 0 until 12) {
                    val major = i % 3 == 0
                    val q = pt(cx, cy, i * 30f, r * 0.82f)
                    p.color = if (major) accent else Color.WHITE
                    c.drawCircle(q.first, q.second, if (major) r * 0.065f else r * 0.035f, p)
                }
            }
        }

        // hands
        val thin = style == 1
        val hw = if (thin) r * 0.035f else r * 0.07f
        val mw = if (thin) r * 0.025f else r * 0.045f
        val hc = if (neon) accent else Color.WHITE
        if (neon) p.setShadowLayer(r * 0.08f, 0f, 0f, accent)
        hand(c, cx, cy, hourA, r * 0.50f, hw, hc)
        hand(c, cx, cy, minA, r * 0.74f, mw, hc)
        p.clearShadowLayer()
        hand(c, cx, cy, secA, r * 0.84f, r * 0.02f, accent)

        // centre
        p.style = Paint.Style.FILL
        p.color = accent
        c.drawCircle(cx, cy, r * 0.05f, p)
    }
}
