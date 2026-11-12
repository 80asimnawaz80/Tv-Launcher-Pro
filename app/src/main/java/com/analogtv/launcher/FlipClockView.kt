package com.analogtv.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import java.util.Calendar

/**
 * HTC style flip-card clock: Hours | Minutes | Seconds (har ek ke 2 cards).
 * Digit badalte hi upar ka patta neeche palat-ta hai. Seconds wale cards accent colour mein chamakte hain.
 */
class FlipClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var accent: Int = 0xFF2D9CFF.toInt()
        set(v) {
            field = v
            invalidate()
        }

    var use24h: Boolean = false
        set(v) {
            field = v
            lastKey = -1L
            invalidate()
        }

    private class Card {
        var cur = -1
        var prev = -1
        var start = 0L
    }

    private val cards = Array(6) { Card() }
    private var amPm = ""
    private var lastKey = -1L
    private val flipMs = 420L

    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        color = Color.WHITE
    }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }
    private val rect = RectF()
    private val clip = Path()

    private val frame = object : Runnable {
        override fun run() {
            update()
            invalidate()
            if (animating()) postOnAnimation(this)
            else postDelayed(this, 1000L - System.currentTimeMillis() % 1000L)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        removeCallbacks(frame)
        post(frame)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(frame)
        super.onDetachedFromWindow()
    }

    private fun animating(): Boolean {
        val now = System.currentTimeMillis()
        return cards.any { it.prev != it.cur && now - it.start < flipMs }
    }

    private fun update() {
        val now = System.currentTimeMillis()
        val key = now / 1000L
        if (key == lastKey) return
        lastKey = key
        val cal = Calendar.getInstance()
        var h = if (use24h) cal.get(Calendar.HOUR_OF_DAY) else cal.get(Calendar.HOUR)
        if (!use24h && h == 0) h = 12
        val m = cal.get(Calendar.MINUTE)
        val s = cal.get(Calendar.SECOND)
        amPm = if (use24h) "" else if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
        val digits = intArrayOf(h / 10, h % 10, m / 10, m % 10, s / 10, s % 10)
        for (i in 0 until 6) {
            val c = cards[i]
            if (c.cur == -1) {
                c.cur = digits[i]
                c.prev = digits[i]
            } else if (c.cur != digits[i]) {
                c.prev = c.cur
                c.cur = digits[i]
                c.start = now
            }
        }
    }

    override fun onDraw(c: Canvas) {
        if (cards[0].cur == -1) update()
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val pairGap = 0.06f
        val groupGap = 0.34f
        val units = 6f + 3f * pairGap + 2f * groupGap
        val ratio = 1.45f
        val ch = minOf(w / units * ratio, h * 0.96f)
        val cw = ch / ratio
        val total = cw * units
        var x = (w - total) / 2f
        val top = (h - ch) / 2f
        val now = System.currentTimeMillis()

        for (g in 0 until 3) {
            for (k in 0..1) {
                val i = g * 2 + k
                drawCard(c, x, top, cw, ch, cards[i], g == 2, now)
                x += cw
                if (k == 0) x += cw * pairGap
            }
            if (g < 2) {
                // colon dots
                val cx = x + cw * groupGap / 2f
                paint.shader = null
                paint.style = Paint.Style.FILL
                paint.color = if (g == 1) ColorUtils.blendARGB(accent, Color.WHITE, 0.5f) else Color.argb(220, 255, 255, 255)
                val r = ch * 0.04f
                c.drawCircle(cx, top + ch * 0.36f, r, paint)
                c.drawCircle(cx, top + ch * 0.64f, r, paint)
                x += cw * groupGap
            }
        }

        // AM / PM (pehle card ke neeche-left kone mein)
        if (amPm.isNotEmpty()) {
            small.textSize = ch * 0.15f
            small.color = Color.argb(200, 255, 255, 255)
            val x0 = (w - total) / 2f
            c.drawText(amPm, x0 + cw * 0.10f, top + ch - ch * 0.07f, small)
        }
    }

    private fun faceColors(isSec: Boolean): IntArray {
        val base = if (isSec) ColorUtils.blendARGB(accent, Color.BLACK, 0.45f)
        else ColorUtils.blendARGB(accent, 0xFF081428.toInt(), 0.84f)
        val topC = ColorUtils.blendARGB(base, Color.WHITE, 0.12f)
        val botC = ColorUtils.blendARGB(base, Color.BLACK, 0.18f)
        return intArrayOf(topC, botC)
    }

    /** Ek half (upar ya neeche) - background + digit. scaleY flip ke liye hinge par se. */
    private fun half(
        c: Canvas, x: Float, y: Float, cw: Float, ch: Float, digit: Int, upper: Boolean,
        sy: Float, shade: Int, isSec: Boolean
    ) {
        if (sy <= 0.01f) return
        val hinge = y + ch / 2f
        val cols = faceColors(isSec)
        c.save()
        c.translate(0f, hinge)
        c.scale(1f, sy)
        c.translate(0f, -hinge)
        if (upper) c.clipRect(x, y, x + cw, hinge) else c.clipRect(x, hinge, x + cw, y + ch)

        paint.style = Paint.Style.FILL
        paint.shader = if (upper) LinearGradient(0f, y, 0f, hinge, cols[0], ColorUtils.blendARGB(cols[0], cols[1], 0.5f), Shader.TileMode.CLAMP)
        else LinearGradient(0f, hinge, 0f, y + ch, ColorUtils.blendARGB(cols[0], cols[1], 0.5f), cols[1], Shader.TileMode.CLAMP)
        rect.set(x, y, x + cw, y + ch)
        val r = cw * 0.14f
        c.drawRoundRect(rect, r, r, paint)
        paint.shader = null

        // digit
        text.textSize = ch * 0.76f
        text.setShadowLayer(ch * 0.03f, 0f, ch * 0.02f, Color.argb(160, 0, 0, 0))
        val fm = text.fontMetrics
        c.drawText(digit.toString(), x + cw / 2f, hinge - (fm.ascent + fm.descent) / 2f, text)
        text.clearShadowLayer()

        // shisha ki chamak (sirf upar ke half par)
        if (upper) {
            paint.shader = LinearGradient(
                0f, y, 0f, hinge,
                Color.argb(95, 255, 255, 255), Color.argb(8, 255, 255, 255), Shader.TileMode.CLAMP
            )
            c.drawRect(x, y, x + cw, hinge, paint)
            paint.shader = null
        }
        if (shade > 0) {
            paint.color = Color.argb(shade, 0, 0, 0)
            c.drawRect(x, if (upper) y else hinge, x + cw, if (upper) hinge else y + ch, paint)
        }
        c.restore()
    }

    private fun drawCard(c: Canvas, x: Float, y: Float, cw: Float, ch: Float, card: Card, isSec: Boolean, now: Long) {
        val t = ((now - card.start).toFloat() / flipMs).coerceIn(0f, 1f)
        val flipping = card.prev != card.cur && t < 1f
        val hinge = y + ch / 2f
        val r = cw * 0.14f

        // bahar ka soft shadow
        paint.shader = null
        paint.style = Paint.Style.FILL
        for (i in 1..3) {
            paint.color = Color.argb(34, 0, 0, 0)
            rect.set(x + i * 0.4f * d, y + i * 1.4f * d, x + cw - i * 0.4f * d, y + ch + i * 1.4f * d)
            c.drawRoundRect(rect, r, r, paint)
        }

        c.save()
        clip.reset()
        rect.set(x, y, x + cw, y + ch)
        clip.addRoundRect(rect, r, r, Path.Direction.CW)
        c.clipPath(clip)

        if (!flipping) {
            half(c, x, y, cw, ch, card.cur, true, 1f, 0, isSec)
            half(c, x, y, cw, ch, card.cur, false, 1f, 0, isSec)
        } else {
            half(c, x, y, cw, ch, card.cur, true, 1f, 0, isSec)    // naya upar (peeche)
            half(c, x, y, cw, ch, card.prev, false, 1f, 0, isSec)  // purana neeche
            if (t < 0.5f) {
                val k = t * 2f
                half(c, x, y, cw, ch, card.prev, true, 1f - k, (110 * k).toInt(), isSec)
            } else {
                val k = (t - 0.5f) * 2f
                half(c, x, y, cw, ch, card.cur, false, k, (110 * (1f - k)).toInt(), isSec)
            }
        }
        c.restore()

        // hinge line
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(230, 0, 0, 0)
        c.drawRect(x, hinge - 0.9f * d, x + cw, hinge + 0.9f * d, paint)
        paint.color = Color.argb(70, 255, 255, 255)
        c.drawRect(x, hinge + 0.9f * d, x + cw, hinge + 1.6f * d, paint)

        // side hinge nubs
        paint.color = Color.argb(200, 235, 248, 255)
        c.drawCircle(x, hinge, cw * 0.05f, paint)
        c.drawCircle(x + cw, hinge, cw * 0.05f, paint)

        // rim light
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = (if (isSec) 1.8f else 1.3f) * d
        paint.shader = LinearGradient(
            0f, y, 0f, y + ch,
            Color.argb(235, 255, 255, 255),
            if (isSec) ColorUtils.setAlphaComponent(ColorUtils.blendARGB(accent, Color.WHITE, 0.35f), 230)
            else ColorUtils.setAlphaComponent(ColorUtils.blendARGB(accent, Color.WHITE, 0.2f), 110),
            Shader.TileMode.CLAMP
        )
        rect.set(x + d * 0.6f, y + d * 0.6f, x + cw - d * 0.6f, y + ch - d * 0.6f)
        c.drawRoundRect(rect, r, r, paint)
        paint.shader = null
        paint.style = Paint.Style.FILL
    }
}
