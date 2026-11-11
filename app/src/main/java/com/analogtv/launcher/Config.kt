package com.analogtv.launcher

import android.content.SharedPreferences

object Config {
    /** Default demo stream (jab koi channel / link set na ho). */
    const val DEFAULT_STREAM = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"

    /** User ka save kiya hua single stream link (khali = default). */
    fun singleUrl(prefs: SharedPreferences): String = prefs.getString("stream", "") ?: ""

    /** Jo stream abhi chalni chahiye: current channel, warna single link, warna default. */
    fun streamUrl(prefs: SharedPreferences): String {
        val ch = Channels.current(prefs)
        if (ch != null) return ch.url
        val s = singleUrl(prefs).trim()
        return if (s.isNotEmpty()) s else DEFAULT_STREAM
    }
}
