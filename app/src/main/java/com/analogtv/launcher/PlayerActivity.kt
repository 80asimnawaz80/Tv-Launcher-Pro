package com.analogtv.launcher

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/** Full-screen Analog TV player (sound ke saath). */
class PlayerActivity : Activity() {

    private var player: ExoPlayer? = null
    private lateinit var view: PlayerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        view = PlayerView(this).apply {
            useController = true
            controllerShowTimeoutMs = 3000
            setBackgroundColor(0xFF000000.toInt())
        }
        setContentView(view)
        hideSystemUi()
    }

    override fun onStart() {
        super.onStart()
        val prefs = getSharedPreferences("analogtv", Context.MODE_PRIVATE)
        val p = ExoPlayer.Builder(this).build()
        p.setMediaItem(MediaItem.fromUri(Config.streamUrl(prefs)))
        p.prepare()
        p.playWhenReady = true
        view.player = p
        player = p
    }

    override fun onStop() {
        view.player = null
        player?.release()
        player = null
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }
}
