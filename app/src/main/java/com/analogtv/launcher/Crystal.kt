package com.analogtv.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import androidx.core.graphics.ColorUtils
import java.util.Random

/**
 * Glassy shining crystal theme:
 *  - bitmap(): faceted crystal wallpaper (theme colour se rang leta hai) + chamakte sparkles
 *  - Glass: shishe jaisa translucent card/button (facets, shine band, rim light)
 */
object Crystal {
    private val cache = HashMap<String, Bitmap>()

    fun bitmap(accent: Int, w: Int, h: Int): Bitmap {
        val key = "$accent/$w/$h"
        cache[key]?.let { return it }

        val dark = ColorUtils.blendARGB(accent, 0xFF050B1A.toInt(), 0.84f)
        val mid = ColorUtils.blendARGB(accent, 0xFF1B1F55.toInt(), 0.58f)
        val lit = ColorUtils.blendARGB(accent, Color.WHITE, 0.14f)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), intArrayOf(dark, mid, lit), floatArrayOf(0f, 0.58f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null

        // ---- crystal facets (jittered triangle grid)
        val rnd = Random(accent.toLong() * 31L + 7L)
        val cols = 10
        val rows = 6
        val cw = w / cols.toFloat()
        val rh = h / rows.toFloat()
        val px = Array(rows + 1) { FloatArray(cols + 1) }
        val py = Array(rows + 1) { FloatArray(cols + 1) }
        for (r in 0..rows) for (q in 0..cols) {
            val edgeX = q == 0 || q == cols
            val edgeY = r == 0 || r == rows
            px[r][q] = q * cw + if (edgeX) 0f else (rnd.nextFloat() - 0.5f) * cw * 0.75f
            py[r][q] = r * rh + if (edgeY) 0f else (rnd.nextFloat() - 0.5f) * rh * 0.75f
        }
        val tri = Path()
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.3f
            color = Color.argb(34, 255, 255, 255)
        }
        fun facet(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float) {
            tri.reset()
            tri.moveTo(ax, ay); tri.lineTo(bx, by); tri.lineTo(cx, cy); tri.close()
            val v = rnd.nextFloat()
            val gx = (ax + bx + cx) / 3f / w
            val bias = 0.18f * (1f - gx) // left side thoda zyada roshan
            p.color = if (v + bias > 0.55f) Color.argb(((v + bias - 0.55f) * 150).toInt().coerceIn(0, 70), 255, 255, 255)
            else Color.argb(((0.55f - v - bias) * 150).toInt().coerceIn(0, 70), 0, 6, 30)
            c.drawPath(tri, p)
            c.drawPath(tri, edge)
        }
        for (r in 0 until rows) for (q in 0 until cols) {
            val a = px[r][q] to py[r][q]
            val b = px[r][q + 1] to py[r][q + 1]
            val d = px[r + 1][q] to py[r + 1][q]
            val e = px[r + 1][q + 1] to py[r + 1][q + 1]
            if ((r + q) % 2 == 0) {
                facet(a.first, a.second, b.first, b.second, e.first, e.second)
                facet(a.first, a.second, e.first, e.second, d.first, d.second)
            } else {
                facet(a.first, a.second, b.first, b.second, d.first, d.second)
                facet(b.first, b.second, e.first, e.second, d.first, d.second)
            }
        }

        // ---- light shafts (tirchi roshni ki patti)
        for (i in 0 until 3) {
            val sx = w * (0.12f + i * 0.33f)
            val band = Path().apply {
                moveTo(sx, 0f); lineTo(sx + w * 0.10f, 0f)
                lineTo(sx - w * 0.26f, h.toFloat()); lineTo(sx - w * 0.36f, h.toFloat()); close()
            }
            p.shader = LinearGradient(sx, 0f, sx - w * 0.3f, h.toFloat(), Color.argb(46, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
            c.drawPath(band, p)
        }
        p.shader = null

        // ---- sparkles
        val star = Path()
        for (i in 0 until 34) {
            val sx = rnd.nextFloat() * w
            val sy = rnd.nextFloat() * h
            val rad = 4f + rnd.nextFloat() * (if (i < 6) 22f else 10f)
            p.shader = RadialGradient(sx, sy, rad * 2.2f, Color.argb(150, 255, 255, 255), 0, Shader.TileMode.CLAMP)
            c.drawCircle(sx, sy, rad * 2.2f, p)
            p.shader = null
            star.reset()
            val th = rad * 0.14f
            star.moveTo(sx, sy - rad * 1.6f); star.lineTo(sx + th, sy - th); star.lineTo(sx + rad * 1.6f, sy)
            star.lineTo(sx + th, sy + th); star.lineTo(sx, sy + rad * 1.6f); star.lineTo(sx - th, sy + th)
            star.lineTo(sx - rad * 1.6f, sy); star.lineTo(sx - th, sy - th); star.close()
            p.color = Color.argb(215, 255, 255, 255)
            c.drawPath(star, p)
        }

        if (cache.size > 6) cache.clear()
        cache[key] = bmp
        return bmp
    }

    /** Shishe ka crystal block: state 0 normal, 1 focused/selected, 2 pressed. */
    class Glass(ctx: Context, radiusDp: Float, private val accent: Int, private val state: Int) : Drawable() {
        private val d = ctx.resources.displayMetrics.density
        private val r = radiusDp * d
        private val depth = 4f * d
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val body = RectF()
        private val tmp = RectF()
        private val clip = Path()
        private val poly = Path()

        override fun onBoundsChange(b: Rect) {
            body.set(b.left + d, b.top + d, b.right - d, b.bottom - depth)
            if (body.width() <= 0f || body.height() <= 0f) return
            val rr = minOf(r, body.height() / 2f)
            clip.reset()
            clip.addRoundRect(body, rr, rr, Path.Direction.CW)
        }

        private fun tri(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, color: Int) {
            val w = body.width()
            val h = body.height()
            poly.reset()
            poly.moveTo(body.left + x1 * w, body.top + y1 * h)
            poly.lineTo(body.left + x2 * w, body.top + y2 * h)
            poly.lineTo(body.left + x3 * w, body.top + y3 * h)
            poly.close()
            paint.style = Paint.Style.FILL
            paint.color = color
            c.drawPath(poly, paint)
        }

        override fun draw(c: Canvas) {
            if (body.width() <= 0f || body.height() <= 0f) return
            val rr = minOf(r, body.height() / 2f)
            val w = body.width()
            val h = body.height()

            // neeche ka shadow
            paint.shader = null
            paint.style = Paint.Style.FILL
            for (i in 1..3) {
                paint.color = Color.argb(if (state == 2) 22 else 36, 0, 0, 0)
                tmp.set(body.left + i * 0.3f * d, body.top + i * 1.3f * d, body.right - i * 0.3f * d, body.bottom + i * 1.3f * d)
                c.drawRoundRect(tmp, rr, rr, paint)
            }

            // focus glow
            if (state == 1) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 7f * d
                paint.color = Color.argb(110, Color.red(accent), Color.green(accent), Color.blue(accent))
                c.drawRoundRect(body, rr, rr, paint)
                paint.style = Paint.Style.FILL
            }

            // shisha ka base (translucent, theme rang ki jhalak)
            val baseColor = when (state) {
                1 -> ColorUtils.blendARGB(accent, Color.WHITE, 0.22f)
                2 -> ColorUtils.blendARGB(accent, Color.BLACK, 0.55f)
                else -> ColorUtils.blendARGB(accent, 0xFF0A1730.toInt(), 0.66f)
            }
            paint.color = ColorUtils.setAlphaComponent(baseColor, if (state == 1) 190 else 158)
            c.drawRoundRect(body, rr, rr, paint)

            c.save()
            c.clipPath(clip)

            // volume: upar chamak, neeche gehra
            paint.shader = LinearGradient(
                0f, body.top, 0f, body.bottom,
                intArrayOf(Color.argb(95, 255, 255, 255), Color.argb(10, 255, 255, 255), Color.argb(80, 0, 8, 30)),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
            )
            c.drawRect(body, paint)
            paint.shader = null

            // crystal facets
            tri(c, 0f, 0f, 0.46f, 0f, 0f, 0.95f, Color.argb(30, 255, 255, 255))
            tri(c, 0.46f, 0f, 1f, 0f, 0.74f, 1f, Color.argb(16, 255, 255, 255))
            tri(c, 0.46f, 0f, 0.74f, 1f, 0.12f, 1f, Color.argb(22, 0, 10, 40))
            tri(c, 1f, 0.15f, 1f, 1f, 0.74f, 1f, Color.argb(26, 255, 255, 255))
            tri(c, 0f, 0.95f, 0.12f, 1f, 0f, 1f, Color.argb(20, 0, 10, 40))

            // tirchi shine band
            val sw = w * 0.20f
            val bx = body.left + w * (if (state == 1) 0.30f else 0.22f)
            poly.reset()
            poly.moveTo(bx, body.top); poly.lineTo(bx + sw, body.top)
            poly.lineTo(bx + sw - h * 0.55f, body.bottom); poly.lineTo(bx - h * 0.55f, body.bottom); poly.close()
            paint.shader = LinearGradient(
                bx - h * 0.2f, 0f, bx + sw, 0f,
                intArrayOf(0, Color.argb(if (state == 1) 120 else 88, 255, 255, 255), 0),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
            )
            c.drawPath(poly, paint)
            paint.shader = null

            // upar ka gloss swoosh
            tmp.set(body.left - w * 0.2f, body.top - h * 0.95f, body.right + w * 0.2f, body.top + h * 0.42f)
            paint.shader = LinearGradient(
                0f, body.top, 0f, body.top + h * 0.42f,
                Color.argb(if (state == 1) 130 else 100, 255, 255, 255), Color.argb(10, 255, 255, 255), Shader.TileMode.CLAMP
            )
            c.drawOval(tmp, paint)
            paint.shader = null
            c.restore()

            // rim light
            paint.style = Paint.Style.STROKE
            val sw2 = (if (state == 1) 2.8f else 1.5f) * d
            paint.strokeWidth = sw2
            paint.shader = LinearGradient(
                0f, body.top, 0f, body.bottom,
                Color.argb(if (state == 1) 255 else 235, 255, 255, 255),
                if (state == 1) Color.WHITE else ColorUtils.setAlphaComponent(ColorUtils.blendARGB(accent, Color.WHITE, 0.4f), 120),
                Shader.TileMode.CLAMP
            )
            tmp.set(body.left + sw2 / 2f, body.top + sw2 / 2f, body.right - sw2 / 2f, body.bottom - sw2 / 2f)
            c.drawRoundRect(tmp, rr, rr, paint)
            paint.shader = null
            paint.style = Paint.Style.FILL
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(cf: ColorFilter?) {
            paint.colorFilter = cf
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
