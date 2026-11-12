package com.analogtv.launcher

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import androidx.core.graphics.ColorUtils

/** 10 theme colours + 3D glossy marble drawables (buttons, cards, PIP bezel). */
object ThemeManager {
    val colors = intArrayOf(
        0xFF2D9CFF.toInt(), // Blue
        0xFF22D3EE.toInt(), // Cyan
        0xFF14B8A6.toInt(), // Teal
        0xFF34D399.toInt(), // Green
        0xFFA3E635.toInt(), // Lime
        0xFFFBBF24.toInt(), // Amber
        0xFFFB923C.toInt(), // Orange
        0xFFF87171.toInt(), // Red
        0xFFF472B6.toInt(), // Pink
        0xFFA78BFA.toInt()  // Purple
    )

    var index = 0

    val accent: Int get() = colors[index.coerceIn(0, colors.size - 1)]

    fun load(prefs: SharedPreferences) {
        index = prefs.getInt("theme", 0).coerceIn(0, colors.size - 1)
    }

    fun save(prefs: SharedPreferences) {
        prefs.edit().putInt("theme", index).apply()
    }

    /** Purana simple background (IPTV screen ke liye). */
    fun background(): Drawable {
        val a = accent
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                ColorUtils.blendARGB(a, Color.BLACK, 0.78f),
                ColorUtils.blendARGB(a, Color.BLACK, 0.90f),
                0xFF05070A.toInt()
            )
        )
    }

    /** Desktop background: marble / picture / colour (BackgroundManager). */
    fun background(ctx: Context): Drawable = BackgroundManager.drawable(ctx)

    /** 3D glossy marble button: normal / focused / pressed / selected. */
    fun selector(ctx: Context, radiusDp: Float): Drawable {
        val a = accent
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), Glass3D(ctx, radiusDp, a, 2))
            addState(intArrayOf(android.R.attr.state_focused), Glass3D(ctx, radiusDp, a, 1))
            addState(intArrayOf(android.R.attr.state_selected), Glass3D(ctx, radiusDp, a, 1))
            addState(intArrayOf(), Glass3D(ctx, radiusDp, a, 0))
        }
    }

    /** PIP ka 3D bezel (video ke upar foreground). */
    fun bezel(ctx: Context): Drawable {
        val a = accent
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), Bezel3D(ctx, a, true))
            addState(intArrayOf(), Bezel3D(ctx, a, false))
        }
    }

    // ============================================================ Glass3D

    /**
     * Chamakta glassy marble block: marble texture + volume gradient + gloss swoosh +
     * rim light + neeche shadow. state: 0 normal, 1 focused/selected, 2 pressed.
     */
    class Glass3D(ctx: Context, radiusDp: Float, private val accent: Int, private val state: Int) : Drawable() {
        private val d = ctx.resources.displayMetrics.density
        private val r = radiusDp * d
        private val depth = 4f * d
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val body = RectF()
        private val tmp = RectF()
        private val clip = Path()
        private val matrix = Matrix()
        private var shader: BitmapShader? = null
        private val marble: Bitmap = Marble.bitmap(
            when (state) {
                1 -> ColorUtils.blendARGB(accent, Color.WHITE, 0.30f)
                2 -> ColorUtils.blendARGB(accent, Color.BLACK, 0.20f)
                else -> accent
            }, 256, 160, true
        )

        override fun onBoundsChange(b: Rect) {
            body.set(b.left + d, b.top + d, b.right - d, b.bottom - depth)
            if (body.width() <= 0f || body.height() <= 0f) return
            val sh = BitmapShader(marble, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            matrix.reset()
            matrix.setScale(body.width() / marble.width, body.height() / marble.height)
            matrix.postTranslate(body.left, body.top)
            sh.setLocalMatrix(matrix)
            shader = sh
            val rr = minOf(r, body.height() / 2f)
            clip.reset()
            clip.addRoundRect(body, rr, rr, Path.Direction.CW)
        }

        override fun draw(c: Canvas) {
            if (body.width() <= 0f || body.height() <= 0f) return
            val rr = minOf(r, body.height() / 2f)

            // neeche ka shadow (3 parte)
            paint.shader = null
            paint.style = Paint.Style.FILL
            for (i in 1..3) {
                paint.color = Color.argb(if (state == 2) 26 else 40, 0, 0, 0)
                tmp.set(body.left + i * 0.3f * d, body.top + i * 1.3f * d, body.right - i * 0.3f * d, body.bottom + i * 1.3f * d)
                c.drawRoundRect(tmp, rr, rr, paint)
            }

            // focus glow
            if (state == 1) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 7f * d
                paint.color = Color.argb(95, Color.red(accent), Color.green(accent), Color.blue(accent))
                c.drawRoundRect(body, rr, rr, paint)
                paint.style = Paint.Style.FILL
            }

            // marble body
            paint.shader = shader
            c.drawRoundRect(body, rr, rr, paint)

            // volume (upar halka, neeche gehra)
            paint.shader = LinearGradient(
                0f, body.top, 0f, body.bottom,
                intArrayOf(Color.argb(60, 255, 255, 255), Color.argb(0, 255, 255, 255), Color.argb(110, 0, 0, 0)),
                floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
            )
            c.drawRoundRect(body, rr, rr, paint)

            // gloss swoosh (upar ka shisha)
            c.save()
            c.clipPath(clip)
            tmp.set(
                body.left - body.width() * 0.25f, body.top - body.height() * 0.95f,
                body.right + body.width() * 0.25f, body.top + body.height() * 0.46f
            )
            paint.shader = LinearGradient(
                0f, body.top, 0f, body.top + body.height() * 0.46f,
                Color.argb(if (state == 1) 150 else 120, 255, 255, 255), Color.argb(18, 255, 255, 255),
                Shader.TileMode.CLAMP
            )
            c.drawOval(tmp, paint)
            c.restore()

            // rim light
            paint.style = Paint.Style.STROKE
            val sw = (if (state == 1) 2.6f else 1.4f) * d
            paint.strokeWidth = sw
            paint.shader = LinearGradient(
                0f, body.top, 0f, body.bottom,
                if (state == 1) Color.WHITE else Color.argb(200, 255, 255, 255),
                if (state == 1) Color.WHITE else Color.argb(60, Color.red(accent), Color.green(accent), Color.blue(accent)),
                Shader.TileMode.CLAMP
            )
            tmp.set(body.left + sw / 2f, body.top + sw / 2f, body.right - sw / 2f, body.bottom - sw / 2f)
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

    // ============================================================ Bezel3D

    /** Video ke upar: chamakta metal frame + shisha ki jhalak. */
    class Bezel3D(ctx: Context, private val accent: Int, private val focused: Boolean) : Drawable() {
        private val d = ctx.resources.displayMetrics.density
        private val r = 16f * d
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()
        private val clip = Path()
        private val sheen = Path()

        override fun onBoundsChange(b: Rect) {
            clip.reset()
            rect.set(b)
            clip.addRoundRect(rect, r, r, Path.Direction.CW)
        }

        override fun draw(c: Canvas) {
            val b = bounds
            if (b.width() <= 0 || b.height() <= 0) return
            val w = b.width().toFloat()
            val h = b.height().toFloat()

            // diagonal shisha ki jhalak
            c.save()
            c.clipPath(clip)
            sheen.reset()
            sheen.moveTo(b.left.toFloat(), b.top.toFloat())
            sheen.lineTo(b.left + w * 0.55f, b.top.toFloat())
            sheen.lineTo(b.left.toFloat(), b.top + h * 0.75f)
            sheen.close()
            paint.style = Paint.Style.FILL
            paint.shader = LinearGradient(
                b.left.toFloat(), b.top.toFloat(), b.left + w * 0.4f, b.top + h * 0.4f,
                Color.argb(48, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP
            )
            c.drawPath(sheen, paint)
            // neeche ka halka andhera (depth)
            paint.shader = LinearGradient(
                0f, b.top + h * 0.7f, 0f, b.bottom.toFloat(),
                Color.argb(0, 0, 0, 0), Color.argb(90, 0, 0, 0), Shader.TileMode.CLAMP
            )
            c.drawRect(b.left.toFloat(), b.top + h * 0.7f, b.right.toFloat(), b.bottom.toFloat(), paint)
            c.restore()

            // bezel (metal ring)
            val bw = (if (focused) 5.5f else 4f) * d
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = bw
            paint.shader = LinearGradient(
                b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(),
                intArrayOf(
                    Color.WHITE,
                    ColorUtils.blendARGB(accent, Color.WHITE, 0.35f),
                    ColorUtils.blendARGB(accent, Color.BLACK, 0.65f),
                    ColorUtils.blendARGB(accent, Color.WHITE, 0.2f)
                ),
                floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP
            )
            rect.set(b.left + bw / 2f, b.top + bw / 2f, b.right - bw / 2f, b.bottom - bw / 2f)
            c.drawRoundRect(rect, r - bw / 2f, r - bw / 2f, paint)

            // andar ki patli kali line
            paint.shader = null
            paint.strokeWidth = 1.2f * d
            paint.color = Color.argb(200, 0, 0, 0)
            rect.set(b.left + bw, b.top + bw, b.right - bw, b.bottom - bw)
            c.drawRoundRect(rect, r - bw, r - bw, paint)
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
