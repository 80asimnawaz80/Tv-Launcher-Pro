package com.analogtv.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Simple vector icons (canvas se bane). `type` number se icon chunta hai.
 * 0 TV/IPTV, 1 settings, 2 info, 3 fullscreen, 4 minus, 5 plus, 6 wifi, 7 prev, 8 next,
 * 9 vol-, 10 vol+, 11 youtube, 12 play store, 13 home, 14 all apps, 15 tv-play,
 * 16 mute, 17 sound, 18 add
 */
class IconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 2f
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()
    private val rect = RectF()

    var type: Int = 0
        set(v) {
            field = v
            invalidate()
        }

    var tint: Int = Color.WHITE
        set(v) {
            field = v
            invalidate()
        }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        val sh = Color.argb(150, 0, 0, 0)
        stroke.setShadowLayer(3f, 1.5f, 2.5f, sh)
        fill.setShadowLayer(3f, 1.5f, 2.5f, sh)
        // Layout me android:tag="7" likh kar bhi icon number diya ja sakta hai
        tag?.toString()?.trim()?.toIntOrNull()?.let { type = it }
        if (attrs != null) {
            for (i in 0 until attrs.attributeCount) {
                if (attrs.getAttributeName(i) == "type") type = attrs.getAttributeIntValue(i, 0)
            }
        }
    }

    override fun onDraw(c: Canvas) {
        val aw = (width - paddingLeft - paddingRight).toFloat()
        val ah = (height - paddingTop - paddingBottom).toFloat()
        val side = minOf(aw, ah)
        if (side <= 0f) return
        val s = side * 0.72f / 24f
        c.save()
        c.translate(paddingLeft + aw / 2f - 12f * s, paddingTop + ah / 2f - 12f * s)
        c.scale(s, s)
        stroke.color = tint
        fill.color = tint
        when (type) {
            0 -> tv(c, false)
            1 -> gear(c)
            2 -> info(c)
            3 -> fullscreen(c)
            4 -> c.drawLine(5f, 12f, 19f, 12f, stroke)
            5 -> plus(c, 6f)
            6 -> wifi(c)
            7 -> skip(c, true)
            8 -> skip(c, false)
            9 -> speaker(c, 1)
            10 -> speaker(c, 2)
            11 -> youtube(c)
            12 -> store(c)
            13 -> home(c)
            14 -> grid(c)
            15 -> tv(c, true)
            16 -> mute(c)
            17 -> speaker(c, 2)
            18 -> {
                c.drawCircle(12f, 12f, 9.5f, stroke)
                plus(c, 4.5f)
            }
            19 -> playTri(c)
            20 -> pauseBars(c)
            21 -> {
                c.drawCircle(12f, 12f, 9.5f, stroke)
                c.drawLine(7.5f, 12f, 16.5f, 12f, stroke)
            }
            22 -> picture(c)
            else -> c.drawCircle(12f, 12f, 8f, stroke)
        }
        c.restore()
    }

    private fun playTri(c: Canvas) {
        path.reset()
        path.moveTo(7f, 4.5f); path.lineTo(7f, 19.5f); path.lineTo(19.5f, 12f)
        path.close()
        c.drawPath(path, fill)
    }

    private fun pauseBars(c: Canvas) {
        rect.set(6f, 5f, 10.5f, 19f)
        c.drawRoundRect(rect, 1.2f, 1.2f, fill)
        rect.set(13.5f, 5f, 18f, 19f)
        c.drawRoundRect(rect, 1.2f, 1.2f, fill)
    }

    private fun picture(c: Canvas) {
        rect.set(2.5f, 4.5f, 21.5f, 19.5f)
        c.drawRoundRect(rect, 2.5f, 2.5f, stroke)
        c.drawCircle(8f, 9.5f, 1.8f, fill)
        path.reset()
        path.moveTo(3.5f, 18.5f); path.lineTo(10f, 12f); path.lineTo(14f, 16f); path.lineTo(16.5f, 13.5f); path.lineTo(20.5f, 18.5f)
        c.drawPath(path, stroke)
    }

    private fun plus(c: Canvas, h: Float) {
        c.drawLine(12f - h, 12f, 12f + h, 12f, stroke)
        c.drawLine(12f, 12f - h, 12f, 12f + h, stroke)
    }

    private fun tv(c: Canvas, play: Boolean) {
        rect.set(2.5f, 6f, 21.5f, 19f)
        c.drawRoundRect(rect, 2.5f, 2.5f, stroke)
        c.drawLine(9f, 3f, 12f, 6f, stroke)
        c.drawLine(15f, 3f, 12f, 6f, stroke)
        if (play) {
            path.reset()
            path.moveTo(10f, 9.5f)
            path.lineTo(10f, 15.5f)
            path.lineTo(15f, 12.5f)
            path.close()
            c.drawPath(path, fill)
        } else {
            c.drawLine(8f, 22f, 16f, 22f, stroke)
        }
    }

    private fun gear(c: Canvas) {
        c.drawCircle(12f, 12f, 3.2f, stroke)
        c.drawCircle(12f, 12f, 7.2f, stroke)
        for (k in 0 until 8) {
            val a = k * PI / 4
            val ca = cos(a).toFloat()
            val sa = sin(a).toFloat()
            c.drawLine(12f + 7.2f * ca, 12f + 7.2f * sa, 12f + 10f * ca, 12f + 10f * sa, stroke)
        }
    }

    private fun info(c: Canvas) {
        c.drawCircle(12f, 12f, 9.5f, stroke)
        c.drawLine(12f, 11f, 12f, 17f, stroke)
        c.drawCircle(12f, 7.6f, 1.2f, fill)
    }

    private fun fullscreen(c: Canvas) {
        corner(c, 4f, 9f, 4f, 4f, 9f, 4f)
        corner(c, 15f, 4f, 20f, 4f, 20f, 9f)
        corner(c, 20f, 15f, 20f, 20f, 15f, 20f)
        corner(c, 9f, 20f, 4f, 20f, 4f, 15f)
    }

    private fun corner(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        path.reset()
        path.moveTo(x1, y1)
        path.lineTo(x2, y2)
        path.lineTo(x3, y3)
        c.drawPath(path, stroke)
    }

    private fun wifi(c: Canvas) {
        c.drawCircle(12f, 19f, 1.6f, fill)
        for (r in floatArrayOf(4.5f, 8f, 11.5f)) {
            rect.set(12f - r, 19f - r, 12f + r, 19f + r)
            c.drawArc(rect, 225f, 90f, false, stroke)
        }
    }

    private fun skip(c: Canvas, prev: Boolean) {
        path.reset()
        if (prev) {
            c.drawLine(6f, 5f, 6f, 19f, stroke)
            path.moveTo(19f, 5f); path.lineTo(19f, 19f); path.lineTo(9f, 12f)
        } else {
            c.drawLine(18f, 5f, 18f, 19f, stroke)
            path.moveTo(5f, 5f); path.lineTo(5f, 19f); path.lineTo(15f, 12f)
        }
        path.close()
        c.drawPath(path, fill)
    }

    private fun speakerBody(c: Canvas) {
        path.reset()
        path.moveTo(3f, 9f); path.lineTo(7.5f, 9f); path.lineTo(13f, 4.5f)
        path.lineTo(13f, 19.5f); path.lineTo(7.5f, 15f); path.lineTo(3f, 15f)
        path.close()
        c.drawPath(path, fill)
    }

    private fun speaker(c: Canvas, waves: Int) {
        speakerBody(c)
        for (i in 0 until waves) {
            val r = 4f + 3.5f * i
            rect.set(13f - r, 12f - r, 13f + r, 12f + r)
            c.drawArc(rect, -45f, 90f, false, stroke)
        }
    }

    private fun mute(c: Canvas) {
        speakerBody(c)
        c.drawLine(16f, 9f, 22f, 15f, stroke)
        c.drawLine(22f, 9f, 16f, 15f, stroke)
    }

    private fun youtube(c: Canvas) {
        rect.set(2f, 5f, 22f, 19f)
        c.drawRoundRect(rect, 4.5f, 4.5f, stroke)
        path.reset()
        path.moveTo(10f, 9f); path.lineTo(10f, 15f); path.lineTo(15.5f, 12f)
        path.close()
        c.drawPath(path, fill)
    }

    private fun store(c: Canvas) {
        path.reset()
        path.moveTo(5f, 3.5f); path.lineTo(5f, 20.5f); path.lineTo(20f, 12f)
        path.close()
        c.drawPath(path, stroke)
        c.drawLine(5f, 3.5f, 14f, 12f, stroke)
        c.drawLine(5f, 20.5f, 14f, 12f, stroke)
    }

    private fun home(c: Canvas) {
        path.reset()
        path.moveTo(3f, 12f); path.lineTo(12f, 4f); path.lineTo(21f, 12f)
        c.drawPath(path, stroke)
        path.reset()
        path.moveTo(6f, 10.5f); path.lineTo(6f, 20f); path.lineTo(18f, 20f); path.lineTo(18f, 10.5f)
        c.drawPath(path, stroke)
    }

    private fun grid(c: Canvas) {
        for (x in floatArrayOf(3.5f, 13.5f)) {
            for (y in floatArrayOf(3.5f, 13.5f)) {
                rect.set(x, y, x + 7f, y + 7f)
                c.drawRoundRect(rect, 1.8f, 1.8f, fill)
            }
        }
    }
}
