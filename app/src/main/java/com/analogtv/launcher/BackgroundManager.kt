package com.analogtv.launcher

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import androidx.core.graphics.ColorUtils
import java.io.File
import java.util.Random
import kotlin.math.sin

/**
 * Desktop background: theme marble / picture (sea, nature, ...) / solid colour / apni photo.
 * Theme colour har surat me upar halki tint se poore desktop par aata hai.
 */
object BackgroundManager {
    const val MODE_THEME = 0      // theme colour ka marble
    const val MODE_SEA = 1
    const val MODE_NATURE = 2
    const val MODE_SUNSET = 3
    const val MODE_NIGHT = 4
    const val MODE_AURORA = 5
    const val MODE_MARBLE_LIGHT = 6
    const val MODE_COLOR = 7      // solid colour (gradient)
    const val MODE_PHOTO = 8      // gallery se apni photo

    val modeNames = arrayOf(
        "Theme marble (default)", "Sea / Samundar", "Nature / Pahar", "Sunset",
        "Night sky", "Aurora", "White marble", "Solid colour...", "Meri photo (gallery)"
    )

    val colorNames = arrayOf("Midnight", "Navy", "Forest", "Wine", "Charcoal", "Plum", "Teal", "Coffee", "Black")
    val colorValues = intArrayOf(
        0xFF0B1026.toInt(), 0xFF0A2A5E.toInt(), 0xFF0F3D26.toInt(), 0xFF4A0F23.toInt(), 0xFF2B2E36.toInt(),
        0xFF3A1B5C.toInt(), 0xFF0B4A4A.toInt(), 0xFF4A2E1B.toInt(), 0xFF000000.toInt()
    )

    private val cache = HashMap<String, Bitmap>()

    fun mode(p: SharedPreferences) = p.getInt("bg_mode", MODE_THEME).coerceIn(0, modeNames.size - 1)
    fun setMode(p: SharedPreferences, m: Int) = p.edit().putInt("bg_mode", m).apply()
    fun setColor(p: SharedPreferences, c: Int) = p.edit().putInt("bg_color", c).putInt("bg_mode", MODE_COLOR).apply()

    fun photoFile(ctx: Context) = File(ctx.filesDir, "wallpaper.jpg")

    /** Gallery se chuni hui photo ko chhota karke save karta hai. */
    fun savePhoto(ctx: Context, uri: android.net.Uri): Boolean = try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        var sample = 1
        while (opts.outWidth / sample > 1920 || opts.outHeight / sample > 1080) sample *= 2
        val bmp = ctx.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
        if (bmp == null) {
            false
        } else {
            photoFile(ctx).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            cache.remove("photo")
            true
        }
    } catch (e: Exception) {
        false
    }

    fun drawable(ctx: Context): Drawable {
        val prefs = ctx.getSharedPreferences("analogtv", Context.MODE_PRIVATE)
        val accent = ThemeManager.accent
        val res = ctx.resources
        val m = mode(prefs)

        val base: Drawable = when (m) {
            MODE_THEME -> BitmapDrawable(res, Marble.bitmap(accent, 480, 270, true))
            MODE_MARBLE_LIGHT -> BitmapDrawable(res, Marble.bitmap(accent, 480, 270, false))
            MODE_COLOR -> {
                val c = prefs.getInt("bg_color", colorValues[0])
                GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(ColorUtils.blendARGB(c, Color.WHITE, 0.12f), c, ColorUtils.blendARGB(c, Color.BLACK, 0.55f))
                )
            }
            MODE_PHOTO -> {
                val f = photoFile(ctx)
                val bmp = cache["photo"] ?: if (f.exists()) BitmapFactory.decodeFile(f.path)?.also { cache["photo"] = it } else null
                if (bmp != null) BitmapDrawable(res, bmp) else BitmapDrawable(res, Marble.bitmap(accent, 480, 270, true))
            }
            else -> BitmapDrawable(res, scene(m))
        }

        // Theme colour ki tint (poora desktop theme se badalta hai) + neeche halka andhera
        val tintAlpha = when (m) {
            MODE_THEME, MODE_MARBLE_LIGHT -> 0
            MODE_COLOR -> 30
            else -> 70
        }
        val tint = ColorDrawable(ColorUtils.setAlphaComponent(accent, tintAlpha))
        val shade = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0x00000000, 0x22000000, 0x66000000)
        )
        return LayerDrawable(arrayOf(base, tint, shade))
    }

    // ------------------------------------------------------------------ procedural scenes

    private fun scene(m: Int): Bitmap {
        val key = "scene$m"
        cache[key]?.let { return it }
        val w = 960
        val h = 540
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        when (m) {
            MODE_SEA -> sea(c, w, h)
            MODE_NATURE -> nature(c, w, h)
            MODE_SUNSET -> sunset(c, w, h)
            MODE_NIGHT -> night(c, w, h, false)
            MODE_AURORA -> night(c, w, h, true)
        }
        if (cache.size > 8) cache.clear()
        cache[key] = b
        return b
    }

    private fun p() = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun sky(c: Canvas, w: Int, h: Int, top: Int, bottom: Int, to: Float) {
        val paint = p()
        paint.shader = LinearGradient(0f, 0f, 0f, h * to, top, bottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h * to, paint)
    }

    private fun sun(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
        val paint = p()
        paint.shader = RadialGradient(x, y, r * 4f, intArrayOf(ColorUtils.setAlphaComponent(color, 200), ColorUtils.setAlphaComponent(color, 60), 0), floatArrayOf(0f, 0.25f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r * 4f, paint)
        val core = p()
        core.color = Color.WHITE
        c.drawCircle(x, y, r, core)
    }

    private fun ridge(c: Canvas, w: Int, h: Int, baseY: Float, amp: Float, seed: Int, color: Int, rough: Float) {
        val rnd = Random(seed.toLong())
        val ph1 = rnd.nextFloat() * 6f
        val ph2 = rnd.nextFloat() * 6f
        val path = Path()
        path.moveTo(0f, h.toFloat())
        var x = 0
        while (x <= w) {
            val y = baseY - amp * (0.55f * sin(x * 0.006f * rough + ph1) + 0.30f * sin(x * 0.017f * rough + ph2) + 0.15f * sin(x * 0.043f * rough + ph1 * 2f))
            path.lineTo(x.toFloat(), y)
            x += 4
        }
        path.lineTo(w.toFloat(), h.toFloat())
        path.close()
        val paint = p()
        paint.color = color
        c.drawPath(path, paint)
    }

    private fun sea(c: Canvas, w: Int, h: Int) {
        sky(c, w, h, 0xFF1E6FD0.toInt(), 0xFFBFE6FF.toInt(), 0.55f)
        sun(c, w * 0.72f, h * 0.36f, 26f, 0xFFFFF2B0.toInt())
        val hor = h * 0.55f
        val water = p()
        water.shader = LinearGradient(0f, hor, 0f, h.toFloat(), 0xFF2FA6D8.toInt(), 0xFF053C73.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, hor, w.toFloat(), h.toFloat(), water)
        // sun glint
        val glint = p()
        glint.color = 0x55FFF8D0
        glint.strokeWidth = 3f
        val rnd = Random(3)
        for (i in 0 until 90) {
            val y = hor + 6f + rnd.nextFloat() * (h - hor - 10f)
            val spread = 20f + (y - hor) * 0.6f
            val x = w * 0.72f + (rnd.nextFloat() - 0.5f) * spread * 2f
            val len = 8f + rnd.nextFloat() * 40f
            c.drawLine(x - len / 2f, y, x + len / 2f, y, glint)
        }
        // waves
        val wave = p()
        wave.style = Paint.Style.STROKE
        for (k in 0 until 14) {
            val y0 = hor + 12f + k * (h - hor) / 14f
            wave.color = ColorUtils.setAlphaComponent(Color.WHITE, 25 + k * 5)
            wave.strokeWidth = 1.5f + k * 0.25f
            val path = Path()
            path.moveTo(0f, y0)
            var x = 0
            while (x <= w) {
                path.lineTo(x.toFloat(), y0 + sin(x * 0.02f + k * 1.7f) * (2f + k * 0.5f))
                x += 6
            }
            c.drawPath(path, wave)
        }
        ridge(c, w, h, hor + 4f, 18f, 11, 0xCC1B4A63.toInt(), 0.5f) // door ka jazeera
        sea2(c, w, h, hor)
    }

    private fun sea2(c: Canvas, w: Int, h: Int, hor: Float) {
        val water = p()
        water.shader = LinearGradient(0f, hor, 0f, h.toFloat(), 0x992FA6D8.toInt(), 0xCC053C73.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, hor + 4f, w.toFloat(), h.toFloat(), water)
    }

    private fun nature(c: Canvas, w: Int, h: Int) {
        sky(c, w, h, 0xFF4FA3E8.toInt(), 0xFFEAF6FF.toInt(), 0.7f)
        sun(c, w * 0.25f, h * 0.22f, 22f, 0xFFFFF3B5.toInt())
        // clouds
        val cl = p()
        cl.color = 0x88FFFFFF.toInt()
        val rnd = Random(5)
        for (i in 0 until 7) {
            val cx = rnd.nextFloat() * w
            val cy = 40f + rnd.nextFloat() * 130f
            for (j in 0 until 5) c.drawCircle(cx + j * 26f, cy + (j % 2) * 7f, 22f + rnd.nextFloat() * 14f, cl)
        }
        ridge(c, w, h, h * 0.55f, 120f, 21, 0xFF6C8DB5.toInt(), 1.0f)
        ridge(c, w, h, h * 0.66f, 90f, 22, 0xFF3F7D5A.toInt(), 1.3f)
        ridge(c, w, h, h * 0.78f, 60f, 23, 0xFF22563A.toInt(), 1.8f)
        // pine trees
        val tp = p()
        for (i in 0 until 46) {
            val x = rnd.nextFloat() * w
            val base = h * 0.80f + rnd.nextFloat() * h * 0.2f
            val sz = 22f + (base - h * 0.8f) * 0.5f + rnd.nextFloat() * 14f
            tp.color = ColorUtils.blendARGB(0xFF123D26.toInt(), 0xFF0A2216.toInt(), rnd.nextFloat())
            for (k in 0 until 3) {
                val path = Path()
                val top = base - sz * (1.8f - k * 0.45f)
                val bw = sz * (0.55f + k * 0.28f)
                path.moveTo(x, top)
                path.lineTo(x - bw, top + sz * 0.9f)
                path.lineTo(x + bw, top + sz * 0.9f)
                path.close()
                c.drawPath(path, tp)
            }
        }
        val grass = p()
        grass.shader = LinearGradient(0f, h * 0.9f, 0f, h.toFloat(), 0x00123D26, 0xFF0A2A18.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, h * 0.9f, w.toFloat(), h.toFloat(), grass)
    }

    private fun sunset(c: Canvas, w: Int, h: Int) {
        val paint = p()
        paint.shader = LinearGradient(
            0f, 0f, 0f, h * 0.7f,
            intArrayOf(0xFF2B1055.toInt(), 0xFF8E2DE2.toInt(), 0xFFFF5E62.toInt(), 0xFFFFC371.toInt()),
            floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w.toFloat(), h * 0.7f, paint)
        sun(c, w * 0.5f, h * 0.62f, 42f, 0xFFFFE9A8.toInt())
        val sea = p()
        sea.shader = LinearGradient(0f, h * 0.7f, 0f, h.toFloat(), 0xFFB5476B.toInt(), 0xFF1B1038.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, h * 0.7f, w.toFloat(), h.toFloat(), sea)
        val g = p()
        g.color = 0x66FFD9A0
        g.strokeWidth = 3f
        val rnd = Random(9)
        for (i in 0 until 70) {
            val y = h * 0.71f + rnd.nextFloat() * h * 0.28f
            val x = w * 0.5f + (rnd.nextFloat() - 0.5f) * (60f + (y - h * 0.7f) * 1.2f)
            c.drawLine(x - 14f, y, x + 14f, y, g)
        }
        ridge(c, w, h, h * 0.72f, 70f, 31, 0xFF1B1038.toInt(), 1.1f)
    }

    private fun night(c: Canvas, w: Int, h: Int, aurora: Boolean) {
        val paint = p()
        paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF02030F.toInt(), 0xFF14264F.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        val rnd = Random(41)
        val st = p()
        for (i in 0 until 220) {
            st.color = ColorUtils.setAlphaComponent(Color.WHITE, 80 + rnd.nextInt(175))
            c.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h * 0.75f, 0.6f + rnd.nextFloat() * 1.4f, st)
        }
        if (aurora) {
            val cols = intArrayOf(0xFF2BFF88.toInt(), 0xFF7B5CFF.toInt(), 0xFF22D3EE.toInt())
            for (k in 0 until 3) {
                val band = p()
                band.shader = LinearGradient(0f, h * 0.1f, 0f, h * 0.65f, intArrayOf(0x00000000, ColorUtils.setAlphaComponent(cols[k], 110), 0x00000000), null, Shader.TileMode.CLAMP)
                val path = Path()
                path.moveTo(0f, h * 0.7f)
                var x = 0
                while (x <= w) {
                    val y = h * (0.30f + 0.08f * k) + sin(x * 0.008f + k * 2f) * 50f + sin(x * 0.02f + k) * 18f
                    path.lineTo(x.toFloat(), y)
                    x += 6
                }
                path.lineTo(w.toFloat(), h * 0.8f)
                path.close()
                c.drawPath(path, band)
            }
        } else {
            val moon = p()
            moon.shader = RadialGradient(w * 0.8f, h * 0.2f, 120f, intArrayOf(0x66FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
            c.drawCircle(w * 0.8f, h * 0.2f, 120f, moon)
            val m2 = p()
            m2.color = 0xFFF4F1DE.toInt()
            c.drawCircle(w * 0.8f, h * 0.2f, 30f, m2)
        }
        ridge(c, w, h, h * 0.80f, 80f, 51, 0xFF050B1C.toInt(), 1.0f)
        ridge(c, w, h, h * 0.90f, 45f, 52, 0xFF02040C.toInt(), 1.7f)
    }
}
