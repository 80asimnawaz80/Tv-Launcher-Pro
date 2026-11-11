package com.analogtv.launcher

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import androidx.core.graphics.ColorUtils

/** 10 glass theme colours + background / button selector drawables. */
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

    /** Glass button: normal / focused / pressed / selected states. */
    fun selector(ctx: Context, radiusDp: Float): Drawable {
        val d = ctx.resources.displayMetrics.density
        val r = radiusDp * d
        val a = accent
        fun box(fill: Int, stroke: Int, w: Float): GradientDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = r
            setColor(fill)
            setStroke((w * d).toInt().coerceAtLeast(1), stroke)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), box(ColorUtils.setAlphaComponent(a, 90), Color.WHITE, 3f))
            addState(intArrayOf(android.R.attr.state_selected), box(ColorUtils.setAlphaComponent(a, 90), Color.WHITE, 3f))
            addState(intArrayOf(android.R.attr.state_pressed), box(ColorUtils.setAlphaComponent(a, 130), a, 2f))
            addState(intArrayOf(), box(0x26FFFFFF, 0x40FFFFFF, 1f))
        }
    }
}
