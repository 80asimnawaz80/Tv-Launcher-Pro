package com.analogtv.launcher

import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import androidx.core.graphics.ColorUtils

object ThemeManager {
    // Blue, Purple, Teal, Green, Gold
    val colors = intArrayOf(
        0xFF2979FF.toInt(),
        0xFFB04DFF.toInt(),
        0xFF1DE9B6.toInt(),
        0xFF76FF03.toInt(),
        0xFFFFC107.toInt()
    )
    var index = 0
    val accent: Int get() = colors[index]

    fun load(p: SharedPreferences) {
        index = p.getInt("theme", 0).coerceIn(0, colors.size - 1)
    }

    fun save(p: SharedPreferences) {
        p.edit().putInt("theme", index).apply()
    }

    fun background(): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(
            0xFF030818.toInt(),
            ColorUtils.blendARGB(0xFF0A1238.toInt(), accent, 0.28f),
            ColorUtils.blendARGB(0xFF0E0526.toInt(), accent, 0.12f)
        )
    )

    private fun glass(ctx: Context, radiusDp: Float, strong: Boolean): GradientDrawable {
        val d = ctx.resources.displayMetrics.density
        val fills = if (strong) intArrayOf(0x70FFFFFF, 0x30FFFFFF) else intArrayOf(0x40FFFFFF, 0x14FFFFFF)
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, fills).apply {
            cornerRadius = radiusDp * d
            setStroke(
                ((if (strong) 3 else 2) * d).toInt(),
                if (strong) accent else ColorUtils.setAlphaComponent(accent, 150)
            )
        }
    }

    fun selector(ctx: Context, radiusDp: Float): StateListDrawable = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), glass(ctx, radiusDp, true))
        addState(intArrayOf(android.R.attr.state_pressed), glass(ctx, radiusDp, true))
        addState(intArrayOf(android.R.attr.state_selected), glass(ctx, radiusDp, true))
        addState(intArrayOf(), glass(ctx, radiusDp, false))
    }
}
