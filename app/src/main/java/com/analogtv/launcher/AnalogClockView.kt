package com.analogtv.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class AnalogClockView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    var accent: Int = Color.CYAN
        set(v) { field = v; invalidate() }

    private val dm = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    private val ticker = object : Runnable {
        override fun run() {
            invalidate()
            postDelayed(this, 1000)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post(ticker)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(ticker)
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val s = if (h > 0) h else (120 * dm).toInt()
        setMeasuredDimension(s, s)
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - 4 * dm

        // face
        p.style = Paint.Style.FILL
        p.color = 0x55000000
        c.drawCircle(cx, cy, r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3 * dm
        p.color = ColorUtils.setAlphaComponent(accent, 220)
        c.drawCircle(cx, cy, r, p)

        // ticks
        p.color = Color.WHITE
        p.strokeCap = Paint.Cap.ROUND
        for (i in 0 until 60) {
            val a = Math.toRadians(i * 6.0)
            val len = if (i % 5 == 0) r * 0.10f else r * 0.05f
            p.strokeWidth = (if (i % 5 == 0) 2f else 1f) * dm
            val x1 = cx + (r * 0.92f) * sin(a).toFloat()
            val y1 = cy - (r * 0.92f) * cos(a).toFloat()
            val x2 = cx + (r * 0.92f - len) * sin(a).toFloat()
            val y2 = cy - (r * 0.92f - len) * cos(a).toFloat()
            c.drawLine(x1, y1, x2, y2, p)
        }

        // numerals 12 / 3 / 6 / 9
        p.style = Paint.Style.FILL
        p.textAlign = Paint.Align.CENTER
        p.textSize = r * 0.22f
        p.isFakeBoldText = true
        val nums = arrayOf("12" to 0.0, "3" to 90.0, "6" to 180.0, "9" to 270.0)
        for ((t, deg) in nums) {
            val a = Math.toRadians(deg)
            val x = cx + r * 0.68f * sin(a).toFloat()
            val y = cy - r * 0.68f * cos(a).toFloat() - (p.descent() + p.ascent()) / 2f
            c.drawText(t, x, y, p)
        }
        p.isFakeBoldText = false

        // hands
        val cal = Calendar.getInstance()
        val sec = cal.get(Calendar.SECOND)
        val min = cal.get(Calendar.MINUTE) + sec / 60f
        val hr = (cal.get(Calendar.HOUR) % 12) + min / 60f

        p.style = Paint.Style.STROKE
        p.color = Color.WHITE
        hand(c, cx, cy, hr * 30f, r * 0.50f, 5 * dm, 0f)
        hand(c, cx, cy, min * 6f, r * 0.75f, 3.5f * dm, 0f)
        p.color = 0xFFFF3B30.toInt()
        hand(c, cx, cy, sec * 6f, r * 0.82f, 1.5f * dm, r * 0.15f)

        p.style = Paint.Style.FILL
        p.color = accent
        c.drawCircle(cx, cy, 5 * dm, p)
    }

    private fun hand(c: Canvas, cx: Float, cy: Float, deg: Float, len: Float, w: Float, tail: Float) {
        p.strokeWidth = w
        c.save()
        c.rotate(deg, cx, cy)
        c.drawLine(cx, cy + tail, cx, cy - len, p)
        c.restore()
    }
}
