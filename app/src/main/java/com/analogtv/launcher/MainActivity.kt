package com.analogtv.launcher

import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class MainActivity : Activity() {

    private enum class Status(val label: String, val bars: Int, val color: Int) {
        CONNECTING("Connecting", 1, 0xFFFFC107.toInt()),
        BUFFERING("Buffering", 2, 0xFFFFC107.toInt()),
        GOOD("Good", 3, 0xFF3DDC4A.toInt()),
        OFFLINE("No Signal", 0, 0xFFFF4D4D.toInt())
    }

    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private var player: ExoPlayer? = null
    private var visible = false
    private var sizeIndex = 1

    private val sizeNames = arrayOf("Mini", "Small", "Medium", "Large")
    private val sizeFractions = floatArrayOf(0.62f, 0.80f, 0.90f, 1.0f)

    private val youtubePkgs = listOf(
        "com.google.android.youtube.tv",
        "com.google.android.youtube.googletv",
        "com.google.android.youtube",
        "com.liskovsoft.smarttubetv.beta",
        "com.teamsmart.videomanager.tv"
    )
    private val playStorePkgs = listOf("com.android.vending")

    private lateinit var root: View
    private lateinit var pipArea: FrameLayout
    private lateinit var pipCard: FrameLayout
    private lateinit var pipPlayerView: PlayerView
    private lateinit var liveBadge: TextView
    private lateinit var qualityText: TextView
    private lateinit var sizeLabel: TextView
    private lateinit var weatherIcon: TextView
    private lateinit var weatherTemp: TextView
    private lateinit var weatherDesc: TextView
    private lateinit var weatherCity: TextView
    private lateinit var sigViews: List<View>
    private lateinit var dots: List<View>

    private val dm: Float get() = resources.displayMetrics.density

    private val retryRunnable = Runnable { player?.prepare() }
    private val weatherRunnable = Runnable { fetchWeather() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("analogtv", Context.MODE_PRIVATE)
        ThemeManager.load(prefs)
        sizeIndex = prefs.getInt("size", 1).coerceIn(0, sizeNames.size - 1)

        setContentView(R.layout.activity_main)
        hideSystemUi()
        bindViews()
        setupClicks()

        pipArea.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applyPipSize(false) }
        pipArea.post { applyPipSize(false) }
        sizeLabel.text = sizeNames[sizeIndex]

        applyTheme()
        findViewById<View>(R.id.tileYoutube).requestFocus()
    }

    override fun onStart() {
        super.onStart()
        visible = true
        ThemeManager.load(prefs)
        applyTheme()
        useRealIcon(findViewById(R.id.tileYoutube), youtubePkgs)
        useRealIcon(findViewById(R.id.tilePlayStore), playStorePkgs)
        startPip()
        fetchWeather()
    }

    override fun onStop() {
        visible = false
        handler.removeCallbacks(weatherRunnable)
        stopPip()
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    // Launcher home screen se back dabane par app band nahi honi chahiye.
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        // intentionally empty
    }

    // ------------------------------------------------------------------ views

    private fun bindViews() {
        root = findViewById(R.id.root)
        pipArea = findViewById(R.id.pipArea)
        pipCard = findViewById(R.id.pipCard)
        pipPlayerView = findViewById(R.id.pipPlayer)
        liveBadge = findViewById(R.id.liveBadge)
        qualityText = findViewById(R.id.qualityText)
        sizeLabel = findViewById(R.id.sizeLabel)
        weatherIcon = findViewById(R.id.weatherIcon)
        weatherTemp = findViewById(R.id.weatherTemp)
        weatherDesc = findViewById(R.id.weatherDesc)
        weatherCity = findViewById(R.id.weatherCity)
        sigViews = listOf<View>(
            findViewById(R.id.sig1), findViewById(R.id.sig2), findViewById(R.id.sig3)
        )
        dots = listOf<View>(
            findViewById(R.id.dot0), findViewById(R.id.dot1), findViewById(R.id.dot2),
            findViewById(R.id.dot3), findViewById(R.id.dot4)
        )

        // PIP ke rounded corners
        pipCard.clipToOutline = true
        pipCard.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, 16f * dm)
            }
        }
        pipCard.elevation = 12f * dm
        pipCard.setBackgroundColor(Color.BLACK)
    }

    private fun setupClicks() {
        dots.forEachIndexed { i, v ->
            v.setOnClickListener {
                ThemeManager.index = i
                ThemeManager.save(prefs)
                applyTheme()
            }
        }
        findViewById<View>(R.id.btnMinus).setOnClickListener { changeSize(-1) }
        findViewById<View>(R.id.btnPlus).setOnClickListener { changeSize(+1) }

        val openFullscreen = View.OnClickListener {
            startActivity(Intent(this, PlayerActivity::class.java))
        }
        pipCard.setOnClickListener(openFullscreen)
        findViewById<View>(R.id.btnFullscreen).setOnClickListener(openFullscreen)
        findViewById<View>(R.id.tilePlayer).setOnClickListener(openFullscreen)
        pipCard.setOnLongClickListener { showStreamDialog(); true }

        findViewById<View>(R.id.weatherCard).setOnClickListener { showCityDialog() }
        findViewById<View>(R.id.btnTopSettings).setOnClickListener { showSettingsDialog() }
        findViewById<View>(R.id.btnInfo).setOnClickListener { showInfoDialog() }

        findViewById<View>(R.id.tileYoutube).setOnClickListener {
            launchFirst(youtubePkgs, "YouTube install nahi hai")
        }
        findViewById<View>(R.id.tilePlayStore).setOnClickListener {
            launchFirst(playStorePkgs, "Play Store is device par nahi hai")
        }
        findViewById<View>(R.id.tileSettings).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                toast("Settings open nahi hui")
            }
        }
        findViewById<View>(R.id.tileAllApps).setOnClickListener {
            startActivity(Intent(this, AllAppsActivity::class.java))
        }
    }

    // ------------------------------------------------------------------ theme

    private fun applyTheme() {
        val a = ThemeManager.accent
        root.background = ThemeManager.background()

        findViewById<AnalogClockView>(R.id.analogClock).accent = a
        findViewById<IconView>(R.id.logo).tint = a

        val title = SpannableString("Analog TV")
        title.setSpan(ForegroundColorSpan(a), 7, 9, 0)
        title.setSpan(StyleSpan(Typeface.BOLD_ITALIC), 7, 9, 0)
        findViewById<TextView>(R.id.appTitle).text = title

        // Glass cards
        findViewById<View>(R.id.weatherCard).background = ThemeManager.selector(this, 28f)
        findViewById<View>(R.id.clockCard).background = ThemeManager.selector(this, 28f)
        findViewById<View>(R.id.optionsBar).background = ThemeManager.selector(this, 31f)

        for (id in intArrayOf(
            R.id.tileYoutube, R.id.tilePlayStore, R.id.tilePlayer, R.id.tileSettings, R.id.tileAllApps
        )) {
            findViewById<View>(id).background = ThemeManager.selector(this, 24f)
        }
        for (id in intArrayOf(
            R.id.btnTopSettings, R.id.btnInfo, R.id.btnFullscreen, R.id.btnMinus, R.id.btnPlus
        )) {
            findViewById<View>(id).background = ThemeManager.selector(this, 40f)
        }

        // PIP frame (foreground border)
        pipCard.foreground = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), pipBorder(a, 4f))
            addState(intArrayOf(), pipBorder(ColorUtils.setAlphaComponent(a, 200), 2.5f))
        }

        // Theme dots
        dots.forEachIndexed { i, v ->
            val c = ThemeManager.colors[i]
            val selected = i == ThemeManager.index
            v.background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), dotDrawable(c, true, true))
                addState(intArrayOf(), dotDrawable(c, selected, false))
            }
        }

        // focus pop animation
        val pops = intArrayOf(
            R.id.tileYoutube, R.id.tilePlayStore, R.id.tilePlayer, R.id.tileSettings, R.id.tileAllApps,
            R.id.btnTopSettings, R.id.btnInfo, R.id.btnFullscreen, R.id.btnMinus, R.id.btnPlus,
            R.id.weatherCard, R.id.dot0, R.id.dot1, R.id.dot2, R.id.dot3, R.id.dot4
        )
        for (id in pops) pop(findViewById(id))

        setStatus(currentStatus)
    }

    private fun pipBorder(color: Int, widthDp: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 16f * dm
        setStroke((widthDp * dm).toInt(), color)
        setColor(Color.TRANSPARENT)
    }

    private fun dotDrawable(color: Int, selected: Boolean, focused: Boolean) =
        GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(ColorUtils.blendARGB(color, Color.WHITE, 0.35f), color)
        ).apply {
            shape = GradientDrawable.OVAL
            val w = if (focused) 4f else if (selected) 3f else 1f
            setStroke((w * dm).toInt(), if (focused || selected) Color.WHITE else 0x66FFFFFF)
        }

    private fun pop(v: View) {
        v.setOnFocusChangeListener { _, focused ->
            val s = if (focused) 1.07f else 1f
            v.animate().scaleX(s).scaleY(s).setDuration(140).start()
        }
    }

    // ------------------------------------------------------------------ PIP size

    private fun changeSize(delta: Int) {
        val n = (sizeIndex + delta).coerceIn(0, sizeNames.size - 1)
        if (n == sizeIndex) return
        sizeIndex = n
        prefs.edit().putInt("size", sizeIndex).apply()
        sizeLabel.text = sizeNames[sizeIndex]
        applyPipSize(true)
    }

    private fun applyPipSize(animate: Boolean) {
        val aw = pipArea.width
        val ah = pipArea.height
        if (aw <= 0 || ah <= 0) return
        val f = sizeFractions[sizeIndex]
        var w = aw * f
        var h = w * 9f / 16f
        if (h > ah * f) {
            h = ah * f
            w = h * 16f / 9f
        }
        val tw = w.toInt()
        val th = h.toInt()
        val lp = pipCard.layoutParams
        if (!animate) {
            if (lp.width != tw || lp.height != th) {
                lp.width = tw
                lp.height = th
                pipCard.layoutParams = lp
            }
            return
        }
        val sw = lp.width
        val sh = lp.height
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 220
            addUpdateListener {
                val t = it.animatedValue as Float
                lp.width = (sw + (tw - sw) * t).toInt()
                lp.height = (sh + (th - sh) * t).toInt()
                pipCard.layoutParams = lp
            }
            start()
        }
    }

    // ------------------------------------------------------------------ PIP player

    private var currentStatus = Status.CONNECTING

    private fun setStatus(s: Status) {
        currentStatus = s
        qualityText.text = "Quality : ${s.label}"
        sigViews.forEachIndexed { i, v ->
            v.setBackgroundColor(if (i < s.bars) s.color else 0x44FFFFFF)
        }
        val good = s == Status.GOOD
        liveBadge.text = if (good) "● LIVE" else "● ${s.label.uppercase()}"
        liveBadge.background = GradientDrawable().apply {
            cornerRadius = 8f * dm
            setColor(if (good) 0xFFE5222B.toInt() else 0xCC555B70.toInt())
        }
    }

    private fun startPip() {
        if (player != null) return
        val p = ExoPlayer.Builder(this).build()
        p.repeatMode = Player.REPEAT_MODE_ONE
        p.volume = if (prefs.getBoolean("sound", false)) 1f else 0f
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> setStatus(Status.GOOD)
                    Player.STATE_BUFFERING -> setStatus(Status.BUFFERING)
                    else -> {}
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                setStatus(Status.OFFLINE)
                handler.removeCallbacks(retryRunnable)
                handler.postDelayed(retryRunnable, 10_000)
            }
        })
        pipPlayerView.player = p
        p.setMediaItem(MediaItem.fromUri(Config.streamUrl(prefs)))
        p.prepare()
        p.playWhenReady = true
        player = p
        setStatus(Status.CONNECTING)
    }

    private fun stopPip() {
        handler.removeCallbacks(retryRunnable)
        pipPlayerView.player = null
        player?.release()
        player = null
    }

    private fun reloadStream() {
        stopPip()
        if (visible) startPip()
    }

    // ------------------------------------------------------------------ weather

    private fun currentCity(): City {
        val name = prefs.getString("city", WeatherRepo.cities[0].name)
        return WeatherRepo.cities.firstOrNull { it.name == name } ?: WeatherRepo.cities[0]
    }

    private fun fetchWeather() {
        val city = currentCity()
        weatherCity.text = "📍 ${city.name}"
        Thread {
            val w = WeatherRepo.fetch(city)
            runOnUiThread {
                if (!visible) return@runOnUiThread
                if (w != null) {
                    weatherIcon.text = w.icon
                    weatherTemp.text = "${w.temp}°C"
                    weatherDesc.text = w.desc
                } else if (weatherTemp.text.contains("--")) {
                    weatherDesc.text = "Offline"
                }
                handler.removeCallbacks(weatherRunnable)
                handler.postDelayed(weatherRunnable, if (w != null) 15 * 60_000L else 60_000L)
            }
        }.start()
    }

    // ------------------------------------------------------------------ dialogs

    private fun showSettingsDialog() {
        val sound = prefs.getBoolean("sound", false)
        val items = arrayOf(
            "Live stream link badlein",
            "Weather city badlein",
            if (sound) "PIP awaaz: ON  (mute karein)" else "PIP awaaz: OFF  (on karein)",
            "Is app ko default launcher banayein"
        )
        AlertDialog.Builder(this)
            .setTitle("Analog TV Settings")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showStreamDialog()
                    1 -> showCityDialog()
                    2 -> {
                        prefs.edit().putBoolean("sound", !sound).apply()
                        player?.volume = if (!sound) 1f else 0f
                    }
                    3 -> openHomeSettings()
                }
            }
            .show()
    }

    private fun showStreamDialog() {
        val et = EditText(this).apply {
            setText(Config.streamUrl(prefs))
            setSingleLine(true)
            setSelectAllOnFocus(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        val pad = (20 * dm).toInt()
        val box = FrameLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(et, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        AlertDialog.Builder(this)
            .setTitle("Live stream link (m3u8 / mp4)")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                val u = et.text.toString().trim()
                if (u.isEmpty()) prefs.edit().remove("stream").apply()
                else prefs.edit().putString("stream", u).apply()
                reloadStream()
            }
            .setNeutralButton("Default") { _, _ ->
                prefs.edit().remove("stream").apply()
                reloadStream()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCityDialog() {
        val names = WeatherRepo.cities.map { it.name }.toTypedArray()
        val cur = names.indexOf(currentCity().name)
        AlertDialog.Builder(this)
            .setTitle("Weather city")
            .setSingleChoiceItems(names, cur) { d, which ->
                prefs.edit().putString("city", names[which]).apply()
                d.dismiss()
                fetchWeather()
            }
            .show()
    }

    private fun showInfoDialog() {
        AlertDialog.Builder(this)
            .setTitle("Analog TV Launcher v1.0")
            .setMessage(
                "• PIP TV par click = full screen\n" +
                    "• PIP TV par long-press = apna live stream link lagayein\n" +
                    "• Theme Color se 5 glass colours, Size (−/+) se PIP chhota/bada\n" +
                    "• Weather card par click = city badlein"
            )
            .setPositiveButton("OK", null)
            .setNeutralButton("Default launcher") { _, _ -> openHomeSettings() }
            .show()
    }

    private fun openHomeSettings() {
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (e2: Exception) {
                toast("Settings open nahi hui")
            }
        }
    }

    // ------------------------------------------------------------------ app helpers

    private fun launchFirst(pkgs: List<String>, notFound: String) {
        for (p in pkgs) {
            val i = packageManager.getLeanbackLaunchIntentForPackage(p)
                ?: packageManager.getLaunchIntentForPackage(p)
            if (i != null) {
                try {
                    startActivity(i)
                    return
                } catch (e: Exception) {
                    // next package try karein
                }
            }
        }
        toast(notFound)
    }

    /** Agar app installed hai to tile me uska asli icon dikhayein, warna generic icon rehne dein. */
    private fun useRealIcon(tile: View, pkgs: List<String>) {
        val group = tile as? ViewGroup ?: return
        val old = group.getChildAt(0)
        if (old !is IconView) return
        for (p in pkgs) {
            try {
                val d = packageManager.getApplicationIcon(p)
                val iv = ImageView(this).apply {
                    setImageDrawable(d)
                    layoutParams = old.layoutParams
                }
                group.removeViewAt(0)
                group.addView(iv, 0)
                return
            } catch (e: PackageManager.NameNotFoundException) {
                // next
            }
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
