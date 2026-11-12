package com.analogtv.launcher

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

/** Procedural marble texture (koi image file nahi chahiye). Theme colour se rang leta hai. */
object Marble {
    private val cache = HashMap<String, Bitmap>()

    private fun hash(x: Int, y: Int, seed: Int): Float {
        var h = x * 374761393 + y * 668265263 + seed * 1442695041
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xFFFF) / 65535f
    }

    private fun smooth(t: Float) = t * t * (3f - 2f * t)

    private fun noise(x: Float, y: Float, seed: Int): Float {
        val xi = x.toInt()
        val yi = y.toInt()
        val xf = smooth(x - xi)
        val yf = smooth(y - yi)
        val a = hash(xi, yi, seed)
        val b = hash(xi + 1, yi, seed)
        val c = hash(xi, yi + 1, seed)
        val d = hash(xi + 1, yi + 1, seed)
        return (a + (b - a) * xf) + ((c + (d - c) * xf) - (a + (b - a) * xf)) * yf
    }

    private fun fbm(x: Float, y: Float, seed: Int): Float {
        var s = 0f
        var amp = 0.5f
        var f = 1f
        for (i in 0 until 4) {
            s += amp * noise(x * f, y * f, seed + i)
            amp *= 0.5f
            f *= 2f
        }
        return s
    }

    /**
     * dark = true: gehra marble (desktop / panels), false: halka chamakta marble.
     */
    fun bitmap(accent: Int, w: Int, h: Int, dark: Boolean): Bitmap {
        val key = "$accent/$w/$h/$dark"
        cache[key]?.let { return it }
        val base = if (dark) ColorUtils.blendARGB(accent, Color.BLACK, 0.68f) else ColorUtils.blendARGB(accent, Color.WHITE, 0.80f)
        val vein = if (dark) ColorUtils.blendARGB(accent, Color.WHITE, 0.62f) else ColorUtils.blendARGB(accent, Color.BLACK, 0.35f)
        val px = IntArray(w * h)
        val sc = 5f / w
        for (y in 0 until h) {
            for (x in 0 until w) {
                val n = fbm(x * sc * 1.6f, y * sc * 1.6f, 7)
                val v = sin((x * sc * 0.9f + y * sc * 0.5f + n * 3.2f) * 3.14159f * 2f)
                var t = (1f - abs(v)).pow(7f)               // patli veins
                val v2 = sin((x * sc * 0.4f - y * sc * 0.9f + n * 4.5f) * 3.14159f * 2f)
                t += (1f - abs(v2)).pow(11f) * 0.6f          // doosri halki veins
                t = (t * 0.85f + n * 0.25f).coerceIn(0f, 1f)
                px[y * w + x] = ColorUtils.blendARGB(base, vein, t)
            }
        }
        val bmp = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        if (cache.size > 12) cache.clear()
        cache[key] = bmp
        return bmp
    }
}
