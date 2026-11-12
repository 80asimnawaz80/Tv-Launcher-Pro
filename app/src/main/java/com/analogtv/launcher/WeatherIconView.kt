package com.analogtv.launcher

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.sin

/**
 * 3D animated weather icon: chamakta suraj / chand, soft shaded badal, girti baarish,
 * barf, kohra aur bijli. `text` me weather emoji dein (WeatherRepo wala), icon khud ban jata hai.
 */
class WeatherIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private enum class Kind { CLEAR, PARTLY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM }

    private var kind = Kind.PARTLY

    var text: CharSequence = ""
        set(v) {
            field = v
            kind = parse(v.toString())
            invalidate()
        }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL)
    }
    private val path = Path()
    private val path2 = Path()
    private val rect = RectF()
    private val t0 = System.nanoTime()

    private val loop = object : Runnable {
        override fun run() {
            invalidate()
            postDelayed(this, 40L)
        }
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null) // blur shadow / glow ke liye
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post(loop)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(loop)
        super.onDetachedFromWindow()
    }

    private fun parse(s: String): Kind = when {
        s.contains("⛈") -> Kind.STORM
        s.contains("🌦") -> Kind.DRIZZLE
        s.contains("🌧") -> Kind.RAIN
        s.contains("❄") || s.contains("🌨") -> Kind.SNOW
        s.contains("🌫") -> Kind.FOG
        s.contains("🌤") || s.contains("⛅") -> Kind.PARTLY
        s.contains("☁") -> Kind.CLOUDY
        s.contains("☀") -> Kind.CLEAR
        else -> Kind.PARTLY
    }

    private fun isNight(): Boolean {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return h < 6 || h >= 19
    }

    override fun onDraw(c: Canvas) {
        val side = minOf(width, height).toFloat()
        if (side <= 0f) return
        val t = (System.nanoTime() - t0) / 1_000_000_000f
        val s = side / 100f
        val night = isNight()
        val dx = sin(t * 0.9f) * 2.5f

        c.save()
        c.translate((width - side) / 2f, (height - side) / 2f + sin(t * 1.6f) * 1.2f * s)
        c.scale(s, s)
        when (kind) {
            Kind.CLEAR -> star(c, 50f, 50f, 21f, t, night)
            Kind.PARTLY -> {
                star(c, 64f, 36f, 16f, t, night)
                cloud(c, 44f + dx, 60f, 1.0f, false)
            }
            Kind.CLOUDY -> {
                cloud(c, 64f - dx, 40f, 0.75f, true)
                cloud(c, 42f + dx, 58f, 1.05f, false)
            }
            Kind.FOG -> {
                cloud(c, 50f + dx, 36f, 1.0f, false)
                fog(c, t)
            }
            Kind.DRIZZLE -> {
                star(c, 68f, 26f, 12f, t, night)
                cloud(c, 46f + dx, 44f, 1.0f, false)
                rain(c, 60f, 4, 36f, t, 24f, false)
            }
            Kind.RAIN -> {
                cloud(c, 50f + dx, 38f, 1.15f, true)
                rain(c, 58f, 7, 52f, t, 32f, true)
            }
            Kind.SNOW -> {
                cloud(c, 50f + dx, 38f, 1.1f, false)
                snow(c, t)
            }
            Kind.STORM -> {
                cloud(c, 50f + dx, 36f, 1.15f, true)
                rain(c, 58f, 5, 52f, t, 30f, true)
                bolt(c, 50f, 54f, t)
            }
        }
        c.restore()
    }

    // ------------------------------------------------------------------ sun / moon

    private fun star(c: Canvas, cx: Float, cy: Float, r: Float, t: Float, night: Boolean) {
        if (night) moon(c, cx, cy, r, t) else sun(c, cx, cy, r, t)
    }

    private fun sun(c: Canvas, cx: Float, cy: Float, r: Float, t: Float) {
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(
            cx, cy, r * 2.5f,
            intArrayOf(Color.argb(170, 255, 214, 90), Color.argb(70, 255, 160, 30), Color.argb(0, 255, 140, 0)),
            floatArrayOf(0f, 0.42f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, r * 2.5f, p)
        p.shader = null

        // ghoomti hui kirnein
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        c.save()
        c.rotate(t * 16f, cx, cy)
        for (k in 0 until 12) {
            c.save()
            c.rotate(k * 30f, cx, cy)
            val pulse = 0.65f + 0.35f * sin(t * 2.2f + k)
            p.strokeWidth = r * 0.16f
            p.color = Color.argb((255 * pulse).toInt(), 255, 205, 60)
            c.drawLine(cx, cy - r * 1.30f, cx, cy - r * (1.55f + 0.25f * pulse), p)
            c.restore()
        }
        c.restore()

        // gola (3D sphere)
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(
            cx - r * 0.35f, cy - r * 0.4f, r * 1.5f,
            intArrayOf(0xFFFFF9D0.toInt(), 0xFFFFC83D.toInt(), 0xFFEE8A00.toInt()),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, r, p)
        p.shader = RadialGradient(
            cx - r * 0.4f, cy - r * 0.45f, r * 0.5f,
            Color.argb(190, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx - r * 0.4f, cy - r * 0.45f, r * 0.5f, p)
        p.shader = null
    }

    private fun moon(c: Canvas, cx: Float, cy: Float, r: Float, t: Float) {
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(
            cx, cy, r * 2.3f,
            Color.argb(120, 170, 190, 255), Color.argb(0, 170, 190, 255), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, r * 2.3f, p)

        path.reset()
        path.addCircle(cx, cy, r, Path.Direction.CW)
        path2.reset()
        path2.addCircle(cx + r * 0.55f, cy - r * 0.28f, r * 0.86f, Path.Direction.CW)
        path.op(path2, Path.Op.DIFFERENCE)
        p.shader = RadialGradient(
            cx - r * 0.5f, cy - r * 0.3f, r * 1.8f,
            intArrayOf(0xFFFFFFF2.toInt(), 0xFFD3DCF2.toInt(), 0xFF8D9BC4.toInt()),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        c.drawPath(path, p)
        p.shader = null

        val stars = arrayOf(floatArrayOf(-1.9f, -1.2f, 0f), floatArrayOf(-1.4f, 1.5f, 1.3f), floatArrayOf(1.9f, -1.6f, 2.4f))
        for (st in stars) {
            val a = (120 + 135 * (0.5f + 0.5f * sin(t * 2.5f + st[2]))).toInt().coerceIn(0, 255)
            p.color = Color.argb(a, 255, 255, 255)
            c.drawCircle(cx + st[0] * r, cy + st[1] * r, r * 0.07f, p)
        }
    }

    // ------------------------------------------------------------------ cloud (3D shaded)

    private fun cloud(c: Canvas, cx: Float, cy: Float, sc: Float, dark: Boolean) {
        path.reset()
        path.addCircle(cx - 17f * sc, cy + 2f * sc, 13f * sc, Path.Direction.CW)
        path2.reset()
        path2.addCircle(cx - 3f * sc, cy - 7f * sc, 17f * sc, Path.Direction.CW)
        path.op(path2, Path.Op.UNION)
        path2.reset()
        path2.addCircle(cx + 15f * sc, cy, 14f * sc, Path.Direction.CW)
        path.op(path2, Path.Op.UNION)
        rect.set(cx - 30f * sc, cy + 1f * sc, cx + 29f * sc, cy + 15f * sc)
        path2.reset()
        path2.addRoundRect(rect, 7f * sc, 7f * sc, Path.Direction.CW)
        path.op(path2, Path.Op.UNION)

        // neeche ka naram shadow
        c.save()
        c.translate(0f, 3.5f * sc)
        shadowP.color = Color.argb(if (dark) 110 else 70, 0, 20, 60)
        c.drawPath(path, shadowP)
        c.restore()

        // body
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(
            0f, cy - 24f * sc, 0f, cy + 15f * sc,
            if (dark) 0xFF8D99B3.toInt() else Color.WHITE,
            if (dark) 0xFF3E475D.toInt() else 0xFFA9BEDC.toInt(),
            Shader.TileMode.CLAMP
        )
        c.drawPath(path, p)

        // andar ki roshni aur gehrai
        c.save()
        c.clipPath(path)
        p.shader = RadialGradient(
            cx - 10f * sc, cy - 16f * sc, 34f * sc,
            Color.argb(if (dark) 90 else 170, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP
        )
        c.drawRect(cx - 40f * sc, cy - 30f * sc, cx + 40f * sc, cy + 20f * sc, p)
        p.shader = LinearGradient(
            0f, cy + 1f * sc, 0f, cy + 15f * sc,
            Color.argb(0, 0, 0, 0), Color.argb(if (dark) 110 else 55, 20, 40, 90), Shader.TileMode.CLAMP
        )
        c.drawRect(cx - 40f * sc, cy + 1f * sc, cx + 40f * sc, cy + 16f * sc, p)
        c.restore()

        // kinara
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 0.9f
        p.color = Color.argb(if (dark) 70 else 120, 255, 255, 255)
        c.drawPath(path, p)
        p.style = Paint.Style.FILL
    }

    // ------------------------------------------------------------------ rain / snow / fog / bolt

    private fun rain(c: Canvas, y0: Float, n: Int, width: Float, t: Float, fall: Float, heavy: Boolean) {
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        for (i in 0 until n) {
            val x = 50f - width / 2f + (i + 0.5f) * width / n
            val f = (t * (if (heavy) 1.5f else 1.0f) + i * 0.37f) % 1f
            val y = y0 + f * fall
            val a = ((1f - f) * 235f).toInt().coerceIn(0, 255)
            p.strokeWidth = 2.6f
            p.color = Color.argb(a, 90, 175, 255)
            c.drawLine(x, y, x - 2.2f, y + 8f, p)
            p.strokeWidth = 1.0f
            p.color = Color.argb(a, 220, 240, 255)
            c.drawLine(x + 0.5f, y + 0.5f, x - 1.1f, y + 5f, p)
        }
        p.style = Paint.Style.FILL
    }

    private fun snow(c: Canvas, t: Float) {
        p.shader = null
        p.style = Paint.Style.FILL
        for (i in 0 until 9) {
            val f = (t * 0.35f + i * 0.23f) % 1f
            val x = 22f + i * 7f + sin(t * 1.8f + i) * 3f
            val y = 58f + f * 34f
            val a = (sin(f * PI) * 255.0).toInt().coerceIn(0, 255)
            val r = 1.6f + (i % 3) * 0.6f
            p.color = Color.argb(a / 3, 200, 230, 255)
            c.drawCircle(x, y, r * 2.2f, p)
            p.color = Color.argb(a, 255, 255, 255)
            c.drawCircle(x, y, r, p)
        }
    }

    private fun fog(c: Canvas, t: Float) {
        p.style = Paint.Style.FILL
        for (i in 0 until 3) {
            val x = sin(t * 0.8f + i * 1.3f) * 7f
            val y = 58f + i * 9f
            rect.set(24f + x + i * 3f, y, 78f + x - i * 3f, y + 4.5f)
            p.shader = LinearGradient(
                rect.left, 0f, rect.right, 0f,
                intArrayOf(Color.argb(0, 225, 232, 245), Color.argb(200, 225, 232, 245), Color.argb(0, 225, 232, 245)),
                null, Shader.TileMode.CLAMP
            )
            c.drawRoundRect(rect, 2.2f, 2.2f, p)
        }
        p.shader = null
    }

    private fun bolt(c: Canvas, cx: Float, cy: Float, t: Float) {
        val k = t % 3f
        val flash = when {
            k < 0.10f -> 1f
            k < 0.18f -> 0.2f
            k < 0.30f -> 0.9f
            k < 0.60f -> 0.4f
            else -> 0.15f
        }
        path.reset()
        path.moveTo(cx + 4f, cy)
        path.lineTo(cx - 8f, cy + 17f)
        path.lineTo(cx - 1f, cy + 17f)
        path.lineTo(cx - 6f, cy + 34f)
        path.lineTo(cx + 10f, cy + 12f)
        path.lineTo(cx + 2f, cy + 12f)
        path.lineTo(cx + 8f, cy)
        path.close()

        shadowP.color = Color.argb((220 * flash).toInt(), 255, 220, 60)
        c.drawPath(path, shadowP)
        val a = (70 + 185 * flash).toInt().coerceIn(0, 255)
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(
            0f, cy, 0f, cy + 34f,
            Color.argb(a, 255, 255, 225), Color.argb(a, 255, 196, 30), Shader.TileMode.CLAMP
        )
        c.drawPath(path, p)
        p.shader = null
    }
}
