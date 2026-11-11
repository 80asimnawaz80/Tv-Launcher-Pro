package com.analogtv.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Full-screen Zavi TV player (awaaz ke saath).
 *  - Live mode (default): UP / DOWN (ya CH+ / CH-) se channel badlein, OK / tap se channel list.
 *  - VOD mode (EXTRA_URL ke saath): movie / episode, seek bar ke saath.
 */
class PlayerActivity : Activity() {

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
    }

    private var player: ExoPlayer? = null
    private lateinit var view: PlayerView
    private lateinit var label: TextView
    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private val hideLabel = Runnable { label.visibility = View.GONE }
    private val retry = Runnable { player?.prepare() }

    private var vodUrl: String? = null
    private var vodTitle: String = "Zavi TV"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        prefs = getSharedPreferences("analogtv", Context.MODE_PRIVATE)
        vodUrl = intent.getStringExtra(EXTRA_URL)
        vodTitle = intent.getStringExtra(EXTRA_TITLE) ?: "Zavi TV"
        val d = resources.displayMetrics.density

        view = PlayerView(this).apply {
            useController = vodUrl != null
            setBackgroundColor(Color.BLACK)
            isFocusable = true
            if (vodUrl == null) setOnClickListener { showChannelList() }
        }
        label = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 22f
            setBackgroundColor(0xAA000000.toInt())
            setPadding((18 * d).toInt(), (10 * d).toInt(), (18 * d).toInt(), (10 * d).toInt())
            visibility = View.GONE
        }
        val root = FrameLayout(this)
        root.addView(view, FrameLayout.LayoutParams(-1, -1))
        root.addView(
            label,
            FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply {
                setMargins((28 * d).toInt(), (24 * d).toInt(), 0, 0)
            }
        )
        setContentView(root)
        hideSystemUi()
    }

    override fun onStart() {
        super.onStart()
        val p = ExoPlayer.Builder(this).build()
        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                showLabel("No Signal - dobara koshish ho rahi hai...")
                handler.removeCallbacks(retry)
                handler.postDelayed(retry, 8000)
            }
        })
        view.player = p
        player = p
        playCurrent()
    }

    override fun onStop() {
        handler.removeCallbacks(retry)
        view.player = null
        player?.release()
        player = null
        super.onStop()
    }

    private fun playCurrent() {
        val p = player ?: return
        val vod = vodUrl
        p.setMediaItem(MediaItem.fromUri(vod ?: Config.streamUrl(prefs)))
        p.prepare()
        p.playWhenReady = true
        if (vod != null) {
            showLabel(vodTitle)
            return
        }
        val ch = Channels.current(prefs)
        if (ch != null) {
            val n = Channels.load(prefs).size
            showLabel("${Channels.index(prefs, n) + 1}. ${ch.name}")
        } else {
            showLabel("Zavi TV")
        }
    }

    private fun showLabel(text: String) {
        label.text = text
        label.visibility = View.VISIBLE
        handler.removeCallbacks(hideLabel)
        handler.postDelayed(hideLabel, 3500)
    }

    private fun change(delta: Int) {
        if (Channels.step(prefs, delta) != null) playCurrent()
    }

    private fun showChannelList() {
        val list = Channels.load(prefs)
        if (list.isEmpty()) {
            showLabel("Channel list khali hai - home par PIP long-press se playlist / IPTV lagayein")
            return
        }
        val names = list.mapIndexed { i, c -> "${i + 1}. ${c.name}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Channels")
            .setSingleChoiceItems(names, Channels.index(prefs, list.size)) { dlg, which ->
                Channels.select(prefs, which)
                dlg.dismiss()
                playCurrent()
            }
            .show()
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (event == null || vodUrl != null) return super.dispatchKeyEvent(event)
        val key = event.keyCode
        val isNext = key == KeyEvent.KEYCODE_CHANNEL_UP || key == KeyEvent.KEYCODE_DPAD_UP ||
            key == KeyEvent.KEYCODE_PAGE_UP
        val isPrev = key == KeyEvent.KEYCODE_CHANNEL_DOWN || key == KeyEvent.KEYCODE_DPAD_DOWN ||
            key == KeyEvent.KEYCODE_PAGE_DOWN
        val isOk = key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER
        if (isNext || isPrev || isOk) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                when {
                    isNext -> change(+1)
                    isPrev -> change(-1)
                    else -> showChannelList()
                }
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }
}
