package com.analogtv.launcher

import android.content.SharedPreferences

object Config {
    // Apna live channel (HLS .m3u8) link yahan lagayein, ya app me PIP par long-press karke badlein.
    const val DEFAULT_STREAM_URL = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"

    fun streamUrl(p: SharedPreferences): String =
        p.getString("stream", DEFAULT_STREAM_URL)?.takeIf { it.isNotBlank() } ?: DEFAULT_STREAM_URL
}
