package com.analogtv.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/** Saare icons Canvas se draw hote hain (koi image asset nahi chahiye). */
class IconView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    var tint: Int = Color.WHITE
        set(v) { field = v; invalidate() }
    var type: Int = 0
        set(v) { field = v; invalidate() }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()

    init {
        if (attrs != null) {
            val a = ctx.obtainStyledAttributes(attrs, R.styleable.IconView)
            type = a.getInt(R.styleable.IconView_iconType, 0)
            tint = a.getColor(R.styleable.IconView_iconTint, Color.WHITE)
            a.recycle()
        }
    }

    private fun stroke(w: Float, col: Int = tint) {
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = w
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.color = col
    }

    private fun fill(col: Int = tint) {
        p.shader = null
        p.style = Paint.Style.FILL
        p.color = col
    }

    private fun tri(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, col: Int) {
        fill(col)
        path.reset()
        path.moveTo(x1, y1); path.lineTo(x2, y2); path.lineTo(x3, y3); path.close()
        c.drawPath(path, p)
    }

    override fun onDraw(c: Canvas) {
        val availW = width - paddingLeft - paddingRight
        val availH = height - paddingTop - paddingBottom
        val size = min(availW, availH).toFloat()
        if (size <= 0f) return
        c.save()
        c.translate(paddingLeft + (availW - size) / 2f, paddingTop + (availH - size) / 2f)
        c.scale(size / 100f, size / 100f)

        when (type) {
            0 -> { // TV
                stroke(7f)
                rect.set(10f, 30f, 90f, 86f)
                c.drawRoundRect(rect, 12f, 12f, p)
                c.drawLine(50f, 30f, 34f, 12f, p)
                c.drawLine(50f, 30f, 66f, 12f, p)
                tri(c, 38f, 46f, 38f, 70f, 62f, 58f, tint)
            }
            1 -> { // Video (generic glyph; tile me asli app icon installed hone par use hota hai)
                stroke(7f)
                rect.set(10f, 24f, 90f, 76f)
                c.drawRoundRect(rect, 18f, 18f, p)
                tri(c, 42f, 38f, 42f, 62f, 64f, 50f, tint)
            }
            2 -> { // Store bag (generic glyph)
                stroke(7f)
                rect.set(18f, 34f, 82f, 86f)
                c.drawRoundRect(rect, 10f, 10f, p)
                rect.set(34f, 12f, 66f, 52f)
                c.drawArc(rect, 180f, 180f, false, p)
                tri(c, 42f, 50f, 42f, 72f, 62f, 61f, tint)
            }
            3 -> { // Player
                stroke(7f)
                c.drawCircle(50f, 50f, 40f, p)
                tri(c, 40f, 32f, 40f, 68f, 70f, 50f, tint)
            }
            4 -> { // Gear
                stroke(8f)
                c.drawCircle(50f, 50f, 26f, p)
                stroke(7f)
                c.drawCircle(50f, 50f, 11f, p)
                stroke(11f)
                p.strokeCap = Paint.Cap.BUTT
                for (i in 0 until 8) {
                    c.save()
                    c.rotate(i * 45f, 50f, 50f)
                    c.drawLine(50f, 22f, 50f, 8f, p)
                    c.restore()
                }
            }
            5 -> { // Apps grid
                stroke(7f)
                rect.set(14f, 14f, 44f, 44f); c.drawRoundRect(rect, 9f, 9f, p)
                rect.set(56f, 14f, 86f, 44f); c.drawRoundRect(rect, 9f, 9f, p)
                rect.set(14f, 56f, 44f, 86f); c.drawRoundRect(rect, 9f, 9f, p)
                rect.set(56f, 56f, 86f, 86f); c.drawRoundRect(rect, 9f, 9f, p)
            }
            6 -> { // WiFi
                stroke(8f)
                for (r in floatArrayOf(16f, 32f, 48f)) {
                    rect.set(50f - r, 76f - r, 50f + r, 76f + r)
                    c.drawArc(rect, 225f, 90f, false, p)
                }
                fill()
                c.drawCircle(50f, 76f, 6f, p)
            }
            7 -> { // Clock
                stroke(7f)
                c.drawCircle(50f, 50f, 38f, p)
                c.drawLine(50f, 50f, 50f, 26f, p)
                c.drawLine(50f, 50f, 68f, 60f, p)
            }
            8 -> { // Calendar
                stroke(7f)
                rect.set(14f, 20f, 86f, 88f)
                c.drawRoundRect(rect, 10f, 10f, p)
                c.drawLine(14f, 40f, 86f, 40f, p)
                c.drawLine(34f, 10f, 34f, 26f, p)
                c.drawLine(66f, 10f, 66f, 26f, p)
                fill()
                for (row in 0..1) for (col in 0..2) {
                    c.drawCircle(30f + col * 20f, 56f + row * 18f, 4.5f, p)
                }
            }
            9 -> { // Info
                stroke(7f)
                c.drawCircle(50f, 50f, 38f, p)
                fill()
                c.drawCircle(50f, 32f, 5f, p)
                stroke(8f)
                c.drawLine(50f, 46f, 50f, 70f, p)
            }
            10 -> { // Palette
                fill(0xFFE8B86A.toInt())
                rect.set(8f, 14f, 92f, 88f)
                c.drawOval(rect, p)
                fill(0xFFE53935.toInt()); c.drawCircle(30f, 42f, 7f, p)
                fill(0xFF1E88E5.toInt()); c.drawCircle(52f, 30f, 7f, p)
                fill(0xFF43A047.toInt()); c.drawCircle(74f, 42f, 7f, p)
                fill(0xFF8E24AA.toInt()); c.drawCircle(30f, 66f, 7f, p)
            }
            11 -> { // Expand / fullscreen
                stroke(8f)
                path.reset()
                path.moveTo(18f, 38f); path.lineTo(18f, 18f); path.lineTo(38f, 18f)
                path.moveTo(62f, 18f); path.lineTo(82f, 18f); path.lineTo(82f, 38f)
                path.moveTo(82f, 62f); path.lineTo(82f, 82f); path.lineTo(62f, 82f)
                path.moveTo(38f, 82f); path.lineTo(18f, 82f); path.lineTo(18f, 62f)
                c.drawPath(path, p)
            }
        }
        c.restore()
    }
}
