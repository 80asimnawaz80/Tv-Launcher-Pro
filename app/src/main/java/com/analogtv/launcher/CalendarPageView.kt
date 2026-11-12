package com.analogtv.launcher

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils

/** 3D calendar page: binder rings, rang-birangi header, bara din ka number, Hijri din ka hara badge. */
class CalendarPageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var day = 1
    private var month = "---"
    private var weekday = "---"
    private var hijriDay = 1
    private var accent = 0xFF2D9CFF.toInt()

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL)
    }
    private val rect = RectF()

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun set(day: Int, month: String, weekday: String, hijriDay: Int, accent: Int) {
        this.day = day
        this.month = month
        this.weekday = weekday
        this.hijriDay = hijriDay
        this.accent = accent
        invalidate()
    }

    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean) {
        p.shader = null
        p.style = Paint.Style.FILL
        p.textAlign = Paint.Align.CENTER
        p.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        p.textSize = size
        p.color = color
        val fm = p.fontMetrics
        c.drawText(s, x, y - (fm.ascent + fm.descent) / 2f, p)
    }

    override fun onDraw(c: Canvas) {
        val side = minOf(width, height).toFloat()
        if (side <= 0f) return
        val s = side / 100f
        c.save()
        c.translate((width - side) / 2f, (height - side) / 2f)
        c.scale(s, s)

        val head = ColorUtils.blendARGB(accent, Color.BLACK, 0.12f)
        val headDark = ColorUtils.blendARGB(accent, Color.BLACK, 0.45f)

        // page ka shadow
        rect.set(8f, 12f, 90f, 96f)
        shadow.color = Color.argb(120, 0, 0, 0)
        c.drawRoundRect(rect, 9f, 9f, shadow)

        // kagaz
        rect.set(6f, 8f, 90f, 92f)
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, 8f, 0f, 92f, Color.WHITE, 0xFFC9D4E3.toInt(), Shader.TileMode.CLAMP)
        c.drawRoundRect(rect, 9f, 9f, p)

        // header (rang + gloss)
        c.save()
        c.clipRect(6f, 8f, 90f, 35f)
        p.shader = LinearGradient(0f, 8f, 0f, 35f, head, headDark, Shader.TileMode.CLAMP)
        c.drawRoundRect(rect, 9f, 9f, p)
        p.shader = LinearGradient(0f, 8f, 0f, 24f, Color.argb(110, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
        c.drawRect(6f, 8f, 90f, 24f, p)
        c.restore()
        // header ke neeche patli lakeer
        p.shader = null
        p.color = Color.argb(70, 0, 0, 0)
        c.drawRect(6f, 35f, 90f, 36.2f, p)

        // binder rings
        for (x in floatArrayOf(28f, 68f)) {
            rect.set(x - 3f, 3f, x + 3f, 15f)
            p.shader = LinearGradient(x - 3f, 0f, x + 3f, 0f, intArrayOf(0xFF6B7587.toInt(), Color.WHITE, 0xFF5A6377.toInt()), null, Shader.TileMode.CLAMP)
            c.drawRoundRect(rect, 3f, 3f, p)
        }
        p.shader = null

        text(c, month, 48f, 23f, 15f, Color.WHITE, true)
        text(c, day.toString(), 44f, 61f, if (day >= 10) 38f else 44f, 0xFF1E2A3A.toInt(), true)
        text(c, weekday, 40f, 84f, 11f, headDark, true)

        // Hijri din ka hara badge (3D)
        val bx = 79f
        val by = 80f
        shadow.color = Color.argb(130, 0, 0, 0)
        c.drawCircle(bx + 1f, by + 3f, 13f, shadow)
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(bx - 4f, by - 5f, 18f, intArrayOf(0xFF6CE6A6.toInt(), 0xFF1FA463.toInt(), 0xFF0B5C36.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(bx, by, 13f, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.4f
        p.color = Color.argb(200, 255, 255, 255)
        c.drawCircle(bx, by, 13f, p)
        p.style = Paint.Style.FILL
        text(c, hijriDay.toString(), bx, by + 0.5f, 14f, Color.WHITE, true)

        c.restore()
    }
}
