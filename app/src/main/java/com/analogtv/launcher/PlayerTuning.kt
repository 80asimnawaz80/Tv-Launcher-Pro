package com.analogtv.launcher

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer

/**
 * Slow internet ke liye player settings: bada buffer, kam quality (PIP me), live stream
 * thodi peeche (offset) taake bar bar ruke nahi.
 */
object PlayerTuning {

    fun newPlayer(ctx: Context, pip: Boolean, slow: Boolean): ExoPlayer {
        val lc = if (slow) {
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(30_000, 90_000, 6_000, 12_000)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
        } else {
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(20_000, 60_000, 3_000, 8_000)
                .build()
        }
        val p = ExoPlayer.Builder(ctx).setLoadControl(lc).build()
        var tp = p.trackSelectionParameters.buildUpon()
        if (pip) tp = tp.setMaxVideoSize(if (slow) 640 else 1280, if (slow) 360 else 720)
        if (pip && slow) tp = tp.setForceLowestBitrate(true)
        p.trackSelectionParameters = tp.build()
        return p
    }

    fun item(url: String, slow: Boolean): MediaItem =
        MediaItem.Builder()
            .setUri(url)
            .setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(if (slow) 25_000L else 12_000L)
                    .build()
            )
            .build()
}
