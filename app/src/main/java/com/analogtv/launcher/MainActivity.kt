package com.analogtv.launcher

import android.Manifest
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
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import android.provider.Settings
import android.text.InputType
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
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
        OFFLINE("No Signal", 0, 0xFFFF4D4D.toInt()),
        PAUSED("Paused", 0, 0xFF9AA0B5.toInt()),
        YOUTUBE("YouTube", 3, 0xFFFF4D4D.toInt())
    }

    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private var player: ExoPlayer? = null
    private var visible = false
    private var sizeIndex = 1
    private var currentStatus = Status.CONNECTING

    private val sizeNames = arrayOf("Mini", "Small", "Medium", "Large")
    private val sizeFractions = floatArrayOf(0.62f, 0.80f, 0.90f, 1.0f)

    private val analogStyleNames = arrayOf("Classic", "Minimal", "Roman", "Dots", "Neon")
    private val digitalStyleNames = arrayOf("Bold 12h", "Mono", "24-Hour", "Accent", "LED Glow")

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
    private lateinit var pipControls: View
    private lateinit var pipToast: TextView
    private lateinit var ctlMute: IconView
    private lateinit var ctlPlay: IconView
    private lateinit var yt: YoutubePip
    private var userPaused = false
    private val reqWallpaper = 7711
    private lateinit var liveBadge: TextView
    private lateinit var qualityText: TextView
    private lateinit var sizeLabel: TextView
    private lateinit var weatherIcon: WeatherIconView
    private lateinit var calPage: CalendarPageView
    private lateinit var calHijri: TextView
    private lateinit var calGreg: TextView
    private lateinit var calEvent: TextView
    private lateinit var calNext: TextView
    private val calendarRunnable = Runnable { updateCalendar() }

    private val widgetIds = linkedMapOf(
        "weather" to R.id.weatherCard,
        "clock" to R.id.clockCard,
        "calendar" to R.id.calendarCard
    )
    private val widgetTitles = mapOf("weather" to "Weather", "clock" to "Clock", "calendar" to "Calendar (Islamic)")
    private val sizeWeights = floatArrayOf(0.6f, 1f, 1.5f)
    private val widgetSizeNames = arrayOf("Small", "Normal", "Large")
    private lateinit var weatherTemp: TextView
    private lateinit var weatherDesc: TextView
    private lateinit var weatherCity: TextView
    private lateinit var channelName: TextView
    private lateinit var tilesRow: LinearLayout
    private lateinit var sigViews: List<View>
    private lateinit var dots: List<View>

    private val dm: Float get() = resources.displayMetrics.density
    private fun dpi(v: Int) = (v * dm).toInt()

    private val retryRunnable = Runnable {
        player?.let {
            it.seekToDefaultPosition()
            it.prepare()
        }
    }
    private val weatherRunnable = Runnable { refreshWeather() }
    private val hideControls = Runnable { pipControls.visibility = View.GONE }
    private val hidePipToast = Runnable { pipToast.visibility = View.GONE }

    // ================================================================ lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("analogtv", Context.MODE_PRIVATE)
        ThemeManager.load(prefs)
        sizeIndex = prefs.getInt("size", 1).coerceIn(0, sizeNames.size - 1)

        setContentView(R.layout.activity_main)
        hideSystemUi()
        bindViews()
        setupClicks()
        rebuildCustomTiles()

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
        resumeMedia()
        refreshWeather()
    }

    override fun onStop() {
        visible = false
        handler.removeCallbacks(weatherRunnable)
        handler.removeCallbacks(calendarRunnable)
        handler.removeCallbacks(hideControls)
        yt.suspend()
        stopPip()
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    // Remote ke CH+ / CH- keys
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_CHANNEL_UP -> {
                changeChannel(+1)
                return true
            }
            KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                changeChannel(-1)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    // Launcher home screen se back dabane par app band nahi honi chahiye.
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        // intentionally empty
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOCATION) resolveAutoLocation(false)
    }

    // ================================================================ views

    private fun bindViews() {
        root = findViewById(R.id.root)
        pipArea = findViewById(R.id.pipArea)
        pipCard = findViewById(R.id.pipCard)
        pipPlayerView = findViewById(R.id.pipPlayer)
        pipControls = findViewById(R.id.pipControls)
        pipToast = findViewById(R.id.pipToast)
        ctlMute = findViewById(R.id.ctlMute)
        ctlPlay = findViewById(R.id.ctlPlay)
        yt = YoutubePip(this, pipCard)
        liveBadge = findViewById(R.id.liveBadge)
        qualityText = findViewById(R.id.qualityText)
        sizeLabel = findViewById(R.id.sizeLabel)
        weatherIcon = findViewById(R.id.weatherIcon)
        calPage = findViewById(R.id.calPage)
        calHijri = findViewById(R.id.calHijri)
        calGreg = findViewById(R.id.calGreg)
        calEvent = findViewById(R.id.calEvent)
        calNext = findViewById(R.id.calNext)
        calHijri.isSelected = true
        calEvent.isSelected = true
        calNext.isSelected = true
        weatherTemp = findViewById(R.id.weatherTemp)
        weatherDesc = findViewById(R.id.weatherDesc)
        weatherCity = findViewById(R.id.weatherCity)
        channelName = findViewById(R.id.channelName)
        tilesRow = findViewById(R.id.tilesRow)
        sigViews = listOf<View>(
            findViewById(R.id.sig1), findViewById(R.id.sig2), findViewById(R.id.sig3)
        )

        // Theme colour dots (ThemeManager.colors ke hisaab se)
        val row = findViewById<LinearLayout>(R.id.dotsRow)
        dots = ThemeManager.colors.indices.map { i ->
            View(this).apply {
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(dpi(26), dpi(26)).apply {
                    setMargins(dpi(3), 0, dpi(3), 0)
                }
                setOnClickListener {
                    ThemeManager.index = i
                    ThemeManager.save(prefs)
                    applyTheme()
                }
            }
        }
        dots.forEach { row.addView(it) }

        // WiFi icon thoda bada + clickable
        val wifi = findViewById<View>(R.id.wifiIcon)
        wifi.layoutParams = wifi.layoutParams.apply {
            width = dpi(42)
            height = dpi(42)
        }
        wifi.setPadding(dpi(6), dpi(6), dpi(6), dpi(6))
        wifi.isClickable = true
        wifi.isFocusable = true

        // PIP ke rounded corners
        pipCard.clipToOutline = true
        pipCard.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, 16f * dm)
            }
        }
        pipCard.elevation = 26f * dm
        pipCard.setBackgroundColor(Color.BLACK)

        pipControls.background = GradientDrawable().apply {
            cornerRadius = 30f * dm
            setColor(0x99000000.toInt())
        }

        // Clock card click = style badlein
        val clockCard = findViewById<View>(R.id.clockCard)
        clockCard.isClickable = true
        clockCard.isFocusable = true
    }

    private fun setupClicks() {
        findViewById<View>(R.id.btnMinus).setOnClickListener { changeSize(-1) }
        findViewById<View>(R.id.btnPlus).setOnClickListener { changeSize(+1) }

        val openFullscreen = View.OnClickListener { openFullscreenPlayer() }
        pipCard.setOnClickListener {
            // Touch screen par pehla tap sirf buttons dikhata hai, doosra fullscreen kholta hai.
            if (pipCard.isInTouchMode && pipControls.visibility != View.VISIBLE) showControls()
            else openFullscreenPlayer()
        }
        findViewById<View>(R.id.btnFullscreen).setOnClickListener(openFullscreen)
        findViewById<View>(R.id.tilePlayer).setOnClickListener { startActivity(Intent(this, PlayerActivity::class.java)) }
        pipCard.setOnLongClickListener { showChannelMenu(); true }
        // PIP par focus ho to LEFT / RIGHT se channel badlein (jab playlist ho)
        pipCard.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && Channels.load(prefs).size > 1) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        changeChannel(+1)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        changeChannel(-1)
                        true
                    }
                    else -> false
                }
            } else {
                false
            }
        }

        // Mouse hover par PIP ke buttons dikhayein
        val hover = View.OnHoverListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> showControls()
                MotionEvent.ACTION_HOVER_EXIT -> {
                    handler.removeCallbacks(hideControls)
                    handler.postDelayed(hideControls, 800)
                }
            }
            false
        }
        pipCard.setOnHoverListener(hover)
        for (id in intArrayOf(R.id.ctlPrev, R.id.ctlVolDown, R.id.ctlPlay, R.id.ctlMute, R.id.ctlVolUp, R.id.ctlNext)) {
            findViewById<View>(id).setOnHoverListener(hover)
        }
        findViewById<View>(R.id.ctlPrev).setOnClickListener { showControls(); changeChannel(-1) }
        findViewById<View>(R.id.ctlNext).setOnClickListener { showControls(); changeChannel(+1) }
        findViewById<View>(R.id.ctlVolDown).setOnClickListener { showControls(); changeVolume(-0.1f) }
        findViewById<View>(R.id.ctlVolUp).setOnClickListener { showControls(); changeVolume(+0.1f) }
        findViewById<View>(R.id.ctlMute).setOnClickListener { showControls(); toggleMute() }
        findViewById<View>(R.id.ctlPlay).setOnClickListener { showControls(); togglePlay() }
        findViewById<View>(R.id.btnWallpaper).setOnClickListener { showBackgroundDialog() }

        findViewById<View>(R.id.weatherCard).setOnClickListener { showCityDialog() }
        findViewById<View>(R.id.clockCard).setOnClickListener { showClockStyleDialog() }
        findViewById<View>(R.id.calendarCard).setOnClickListener { showCalendarDialog() }
        findViewById<View>(R.id.weatherCard).setOnLongClickListener { showWidgetDialog("weather"); true }
        findViewById<View>(R.id.clockCard).setOnLongClickListener { showWidgetDialog("clock"); true }
        findViewById<View>(R.id.calendarCard).setOnLongClickListener { showWidgetDialog("calendar"); true }
        findViewById<View>(R.id.btnTopSettings).setOnClickListener { showSettingsDialog() }
        findViewById<View>(R.id.btnInfo).setOnClickListener { showInfoDialog() }
        findViewById<View>(R.id.wifiIcon).setOnClickListener { openWifiSettings() }

        findViewById<View>(R.id.tileYoutube).setOnClickListener {
            launchFirst(youtubePkgs, "YouTube install nahi hai")
        }
        findViewById<View>(R.id.tileYoutube).setOnLongClickListener { showYoutubeDialog(); true }
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

    // ================================================================ theme

    private fun applyTheme() {
        val a = ThemeManager.accent
        root.background = ThemeManager.background(this)
        findViewById<IconView>(R.id.logo).tint = a

        val title = SpannableString("Zavi TV")
        title.setSpan(ForegroundColorSpan(a), 5, 7, 0)
        title.setSpan(StyleSpan(Typeface.BOLD_ITALIC), 5, 7, 0)
        findViewById<TextView>(R.id.appTitle).text = title

        findViewById<View>(R.id.weatherCard).background = ThemeManager.selector(this, 28f)
        findViewById<View>(R.id.clockCard).background = ThemeManager.selector(this, 28f)
        findViewById<View>(R.id.calendarCard).background = ThemeManager.selector(this, 28f)
        findViewById<View>(R.id.optionsBar).background = ThemeManager.selector(this, 31f)

        // Bottom tiles (built-in + custom)
        for (i in 0 until tilesRow.childCount) {
            val t = tilesRow.getChildAt(i)
            t.background = ThemeManager.selector(this, 24f)
            pop(t)
        }
        for (id in intArrayOf(
            R.id.btnTopSettings, R.id.btnInfo, R.id.btnFullscreen, R.id.btnMinus, R.id.btnPlus,
            R.id.wifiIcon, R.id.btnWallpaper, R.id.ctlPrev, R.id.ctlVolDown, R.id.ctlPlay,
            R.id.ctlMute, R.id.ctlVolUp, R.id.ctlNext
        )) {
            findViewById<View>(id).background = ThemeManager.selector(this, 40f)
        }

        // PIP frame (foreground border)
        pipCard.foreground = ThemeManager.bezel(this)

        // Theme dots
        dots.forEachIndexed { i, v ->
            val c = ThemeManager.colors[i]
            val selected = i == ThemeManager.index
            v.background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), dotDrawable(c, true, true))
                addState(intArrayOf(), dotDrawable(c, selected, false))
            }
            pop(v)
        }

        // focus pop animation
        for (id in intArrayOf(
            R.id.btnTopSettings, R.id.btnInfo, R.id.btnFullscreen, R.id.btnMinus, R.id.btnPlus,
            R.id.weatherCard, R.id.clockCard, R.id.calendarCard, R.id.wifiIcon, R.id.btnWallpaper
        )) pop(findViewById(id))

        applyClockStyle()
        updateMuteIcon()
        updatePlayIcon()
        applyWidgets()
        updateCalendar()
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

    // ================================================================ clock styles

    private fun applyClockStyle() {
        val a = ThemeManager.accent
        findViewById<AnalogClockView>(R.id.analogClock).apply {
            accent = a
            style = prefs.getInt("analogStyle", 0).coerceIn(0, analogStyleNames.size - 1)
        }
        val dc = findViewById<TextClock>(R.id.digitalClock)
        dc.setShadowLayer(8f, 3f, 4f, 0xAA000000.toInt())
        when (prefs.getInt("digitalStyle", 0).coerceIn(0, digitalStyleNames.size - 1)) {
            0 -> digital(dc, "hh:mm:ss a", Typeface.DEFAULT_BOLD, Color.WHITE, 28f)
            1 -> digital(dc, "hh:mm:ss", Typeface.MONOSPACE, Color.WHITE, 30f)
            2 -> digital(dc, "HH:mm:ss", Typeface.DEFAULT_BOLD, Color.WHITE, 32f)
            3 -> digital(dc, "hh:mm:ss a", Typeface.create("sans-serif-condensed", Typeface.BOLD), a, 32f)
            else -> {
                digital(dc, "HH:mm:ss", Typeface.MONOSPACE, a, 32f)
                dc.setShadowLayer(16f, 0f, 0f, a)
            }
        }
    }

    private fun digital(dc: TextClock, fmt: String, tf: Typeface, color: Int, sizeSp: Float) {
        dc.format12Hour = fmt
        dc.format24Hour = fmt
        dc.typeface = if (tf === Typeface.MONOSPACE) Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) else tf
        dc.setTextColor(color)
        dc.textSize = sizeSp
    }

    private fun showClockStyleDialog() {
        val items = (analogStyleNames.map { "Analog watch: $it" } + digitalStyleNames.map { "Digital clock: $it" })
            .toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Clock style")
            .setItems(items) { _, which ->
                if (which < analogStyleNames.size) prefs.edit().putInt("analogStyle", which).apply()
                else prefs.edit().putInt("digitalStyle", which - analogStyleNames.size).apply()
                applyClockStyle()
            }
            .show()
    }

    // ================================================================ PIP size

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

    // ================================================================ PIP controls

    private fun showControls() {
        pipControls.visibility = View.VISIBLE
        handler.removeCallbacks(hideControls)
        handler.postDelayed(hideControls, 3500)
    }

    private fun pipMessage(text: String) {
        pipToast.text = text
        pipToast.visibility = View.VISIBLE
        handler.removeCallbacks(hidePipToast)
        handler.postDelayed(hidePipToast, 1500)
    }

    private fun volume(): Float = prefs.getFloat("vol", 0.7f)
    private fun isSound(): Boolean = prefs.getBoolean("sound", false)

    private fun applyVolume() {
        player?.volume = if (isSound()) volume() else 0f
        if (yt.active) yt.setSound(isSound(), Math.round(volume() * 100))
        updateMuteIcon()
    }

    private fun updateMuteIcon() {
        ctlMute.type = if (isSound() && volume() > 0f) 17 else 16
    }

    private fun changeVolume(delta: Float) {
        val v = (volume() + delta).coerceIn(0f, 1f)
        prefs.edit().putFloat("vol", v).putBoolean("sound", v > 0f).apply()
        applyVolume()
        pipMessage("Volume ${Math.round(v * 100)}%")
    }

    private fun toggleMute() {
        val on = !isSound()
        val e = prefs.edit().putBoolean("sound", on)
        if (on && volume() <= 0f) e.putFloat("vol", 0.5f)
        e.apply()
        applyVolume()
        pipMessage(if (on) "Sound ON" else "Muted")
    }

    // ================================================================ PIP player

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
        userPaused = false
        updatePlayIcon()
        val p = PlayerTuning.newPlayer(this, true, slowMode())
        p.repeatMode = Player.REPEAT_MODE_ONE
        p.volume = if (isSound()) volume() else 0f
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> if (!userPaused) setStatus(Status.GOOD)
                    Player.STATE_BUFFERING -> if (!userPaused) setStatus(Status.BUFFERING)
                    else -> {}
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                setStatus(Status.OFFLINE)
                handler.removeCallbacks(retryRunnable)
                handler.postDelayed(retryRunnable, 6_000)
            }
        })
        pipPlayerView.player = p
        p.setMediaItem(PlayerTuning.item(Config.streamUrl(prefs), slowMode()))
        p.prepare()
        p.playWhenReady = true
        player = p
        setStatus(Status.CONNECTING)
        updateChannelName()
    }

    private fun stopPip() {
        handler.removeCallbacks(retryRunnable)
        pipPlayerView.player = null
        player?.release()
        player = null
    }

    private fun reloadStream() {
        if (yt.active) {
            yt.stop()
            prefs.edit().remove("yt_active").apply()
        }
        stopPip()
        if (visible) startPip()
    }

    private fun changeChannel(delta: Int) {
        if (yt.active) {
            if (delta > 0) yt.next() else yt.prev()
            return
        }
        if (Channels.step(prefs, delta) != null) playCurrent()
        else toast("Channel list khali hai - PIP par long-press karke playlist (M3U) lagayein")
    }

    private fun playCurrent() {
        val p = player ?: return
        handler.removeCallbacks(retryRunnable)
        userPaused = false
        updatePlayIcon()
        p.setMediaItem(PlayerTuning.item(Config.streamUrl(prefs), slowMode()))
        p.prepare()
        p.playWhenReady = true
        setStatus(Status.CONNECTING)
        updateChannelName()
    }

    private fun updateChannelName() {
        val list = Channels.load(prefs)
        val ch = Channels.current(prefs)
        channelName.text =
            if (ch != null) "${Channels.index(prefs, list.size) + 1}. ${ch.name}" else "Zavi TV"
    }

    // ================================================================ weather / location

    private fun currentCity(): City {
        val name = prefs.getString("city", null)
        val lat = prefs.getString("lat", null)?.toDoubleOrNull()
        val lon = prefs.getString("lon", null)?.toDoubleOrNull()
        if (name != null && lat != null && lon != null) return City(name, lat, lon)
        return WeatherRepo.cities.firstOrNull { it.name == name } ?: WeatherRepo.cities[0]
    }

    private fun saveCity(c: City, auto: Boolean) {
        prefs.edit()
            .putString("city", c.name)
            .putString("lat", c.lat.toString())
            .putString("lon", c.lon.toString())
            .putBoolean("auto", auto)
            .apply()
    }

    /** Auto (current location) mode me pehle location dobara dhoondta hai, warna seedha weather. */
    private fun refreshWeather() {
        if (prefs.getBoolean("auto", false)) resolveAutoLocation(true) else fetchWeather()
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

    private fun useCurrentLocation() {
        val granted = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            // Jawab aane par onRequestPermissionsResult resolveAutoLocation() chalayega
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
                REQ_LOCATION
            )
        } else {
            resolveAutoLocation(false)
        }
    }

    /** GPS/network se location, na mile to internet (IP) se andaza. */
    private fun resolveAutoLocation(silent: Boolean) {
        if (!silent) toast("Current location dhoondi ja rahi hai...")
        Thread {
            var lat: Double? = null
            var lon: Double? = null
            var name: String? = null
            try {
                val has = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (has) {
                    val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
                    var best: Location? = null
                    for (prov in lm.getProviders(true)) {
                        val l = try {
                            lm.getLastKnownLocation(prov)
                        } catch (e: SecurityException) {
                            null
                        }
                        if (l != null && (best == null || l.time > best.time)) best = l
                    }
                    if (best != null) {
                        lat = best.latitude
                        lon = best.longitude
                    }
                }
            } catch (e: Exception) {
                // IP se try karenge
            }
            if (lat == null || lon == null) {
                val ip = WeatherRepo.ipLocation()
                if (ip != null) {
                    lat = ip.lat
                    lon = ip.lon
                    name = ip.name
                }
            }
            val la = lat
            val lo = lon
            if (la != null && lo != null) {
                val nm = name ?: WeatherRepo.reverseName(la, lo) ?: "Current location"
                saveCity(City(nm, la, lo), true)
            }
            runOnUiThread {
                if (!visible) return@runOnUiThread
                if (la == null || lo == null) {
                    if (!silent) {
                        prefs.edit().putBoolean("auto", false).apply()
                        toast("Location nahi mili. List se shehar chunein ya naam likhein")
                    }
                }
                fetchWeather()
            }
        }.start()
    }

    // ================================================================ custom app tiles

    private fun customApps(): List<String> =
        (prefs.getString("customApps", "") ?: "").split(",").filter { it.isNotBlank() }

    private fun saveCustomApps(list: List<String>) {
        prefs.edit().putString("customApps", list.joinToString(",")).apply()
    }

    private fun rebuildCustomTiles() {
        for (i in tilesRow.childCount - 1 downTo 0) {
            val tag = tilesRow.getChildAt(i).tag
            if (tag == "custom" || tag == "add" || tag == "iptv") tilesRow.removeViewAt(i)
        }
        tilesRow.addView(makeIptvTile())
        for (pkg in customApps()) makeCustomTile(pkg)?.let { tilesRow.addView(it) }
        tilesRow.addView(makeAddTile())
        if (customApps().isNotEmpty()) tilesRow.addView(makeRemoveTile())
        layoutTiles()
        if (::dots.isInitialized) applyTheme()
    }

    private fun tileParams() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
        setMargins(dpi(5), dpi(5), dpi(5), dpi(5))
    }

    private fun layoutTiles() {
        val n = tilesRow.childCount
        for (i in 0 until n) {
            val v = tilesRow.getChildAt(i)
            val lp = v.layoutParams as LinearLayout.LayoutParams
            if (n > 8) {
                lp.width = dpi(150)
                lp.weight = 0f
            } else {
                lp.width = 0
                lp.weight = 1f
            }
            v.layoutParams = lp
        }
    }

    private fun tileLabel(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
        textSize = 18f
        setTypeface(typeface, Typeface.BOLD)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.CENTER
        setPadding(dpi(8), dpi(4), dpi(8), 0)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun makeCustomTile(pkg: String): View? {
        val info = try {
            packageManager.getApplicationInfo(pkg, 0)
        } catch (e: Exception) {
            return null
        }
        val label = packageManager.getApplicationLabel(info).toString()
        val icon = packageManager.getApplicationIcon(info)
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            tag = "custom"
            layoutParams = tileParams()
        }
        tile.addView(ImageView(this).apply {
            setImageDrawable(icon)
            layoutParams = LinearLayout.LayoutParams(dpi(48), dpi(48))
        })
        tile.addView(tileLabel(label))
        tile.setOnClickListener { launchFirst(listOf(pkg), "$label open nahi ho saki") }
        tile.setOnLongClickListener {
            AlertDialog.Builder(this)
                .setTitle(label)
                .setMessage("Is app ko home screen se hatayein?")
                .setPositiveButton("Hatayein") { _, _ ->
                    saveCustomApps(customApps().filter { it != pkg })
                    rebuildCustomTiles()
                }
                .setNegativeButton("Cancel", null)
                .show()
            true
        }
        return tile
    }

    private fun makeIptvTile(): View {
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            tag = "iptv"
            layoutParams = tileParams()
        }
        tile.addView(IconView(this).apply {
            type = 0
            layoutParams = LinearLayout.LayoutParams(dpi(48), dpi(48))
        })
        tile.addView(tileLabel("IPTV"))
        tile.setOnClickListener { openIptv() }
        // Long-press = account badlein
        tile.setOnLongClickListener {
            showXtreamLogin(prefs) { startActivity(Intent(this, IptvActivity::class.java)) }
            true
        }
        return tile
    }

    private fun openIptv() {
        if ((prefs.getString("xt_host", "") ?: "").isNotBlank()) {
            startActivity(Intent(this, IptvActivity::class.java))
        } else {
            showXtreamLogin(prefs) { startActivity(Intent(this, IptvActivity::class.java)) }
        }
    }

    private fun makeAddTile(): View {
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            tag = "add"
            layoutParams = tileParams()
        }
        tile.addView(IconView(this).apply {
            type = 18
            layoutParams = LinearLayout.LayoutParams(dpi(48), dpi(48))
        })
        tile.addView(tileLabel("Add App"))
        tile.setOnClickListener { showAddAppDialog() }
        return tile
    }

    private fun showAddAppDialog() {
        Thread {
            val pm = packageManager
            val already = customApps().toSet()
            val seen = HashSet<String>()
            val apps = ArrayList<Pair<String, String>>()
            for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
                val q = Intent(Intent.ACTION_MAIN).addCategory(cat)
                for (ri in pm.queryIntentActivities(q, 0)) {
                    val pkg = ri.activityInfo.packageName
                    if (pkg == packageName || pkg in already || !seen.add(pkg)) continue
                    apps.add(ri.loadLabel(pm).toString() to pkg)
                }
            }
            apps.sortBy { it.first.lowercase() }
            runOnUiThread {
                if (!visible) return@runOnUiThread
                if (apps.isEmpty()) {
                    toast("Koi app nahi mili")
                    return@runOnUiThread
                }
                AlertDialog.Builder(this)
                    .setTitle("Home par kaun si app add karni hai?")
                    .setItems(apps.map { it.first }.toTypedArray()) { _, which ->
                        saveCustomApps(customApps() + apps[which].second)
                        rebuildCustomTiles()
                        toast("${apps[which].first} add ho gayi (hatane ke liye Remove App)")
                    }
                    .show()
            }
        }.start()
    }

    // ================================================================ play / pause, slow mode

    private fun slowMode(): Boolean = prefs.getBoolean("slow", true)

    private fun togglePlay() {
        if (yt.active) {
            userPaused = !userPaused
            if (userPaused) yt.pause() else yt.play()
            updatePlayIcon()
            pipMessage(if (userPaused) "Paused" else "Play")
            return
        }
        val p = player ?: return
        if (userPaused) {
            userPaused = false
            p.seekToDefaultPosition()
            p.prepare()
            p.playWhenReady = true
            setStatus(Status.CONNECTING)
            pipMessage("Play")
        } else {
            userPaused = true
            p.playWhenReady = false
            setStatus(Status.PAUSED)
            pipMessage("Paused")
        }
        updatePlayIcon()
    }

    private fun updatePlayIcon() {
        ctlPlay.type = if (userPaused) 19 else 20
    }

    // ================================================================ remove app tile

    private fun makeRemoveTile(): View {
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            tag = "add"
            layoutParams = tileParams()
        }
        tile.addView(IconView(this).apply {
            type = 21
            layoutParams = LinearLayout.LayoutParams(dpi(48), dpi(48))
        })
        tile.addView(tileLabel("Remove App"))
        tile.setOnClickListener { showRemoveAppDialog() }
        return tile
    }

    private fun showRemoveAppDialog() {
        val pkgs = customApps()
        if (pkgs.isEmpty()) {
            toast("Hatane ke liye koi app nahi")
            return
        }
        val labels = pkgs.map {
            try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(it, 0)).toString()
            } catch (e: Exception) {
                it
            }
        }
        AlertDialog.Builder(this)
            .setTitle("Kaun si app hatani hai?")
            .setItems(labels.toTypedArray()) { _, which ->
                saveCustomApps(customApps().filter { it != pkgs[which] })
                rebuildCustomTiles()
                toast("${labels[which]} hata di gayi")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ================================================================ background / wallpaper

    private fun showBackgroundDialog() {
        AlertDialog.Builder(this)
            .setTitle("Desktop background")
            .setItems(BackgroundManager.modeNames) { _, which ->
                when (which) {
                    BackgroundManager.MODE_COLOR -> showBgColorDialog()
                    BackgroundManager.MODE_PHOTO -> pickPhoto()
                    else -> {
                        BackgroundManager.setMode(prefs, which)
                        applyTheme()
                    }
                }
            }
            .show()
    }

    private fun showBgColorDialog() {
        AlertDialog.Builder(this)
            .setTitle("Background colour")
            .setItems(BackgroundManager.colorNames) { _, i ->
                BackgroundManager.setColor(prefs, BackgroundManager.colorValues[i])
                applyTheme()
            }
            .show()
    }

    @Suppress("DEPRECATION")
    private fun pickPhoto() {
        try {
            val i = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(Intent.createChooser(i, "Photo chunein"), reqWallpaper)
        } catch (e: Exception) {
            toast("Gallery / file manager nahi mila")
        }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == reqWallpaper && resultCode == RESULT_OK) {
            val uri = data?.data
            if (uri == null) {
                toast("Photo nahi mili")
                return
            }
            Thread {
                val ok = BackgroundManager.savePhoto(this, uri)
                runOnUiThread {
                    if (ok) {
                        BackgroundManager.setMode(prefs, BackgroundManager.MODE_PHOTO)
                        applyTheme()
                        toast("Wallpaper lag gaya")
                    } else {
                        toast("Photo load nahi ho saki")
                    }
                }
            }.start()
        }
    }

    // ================================================================ Islamic calendar widget

    private fun hijriAdj(): Int = prefs.getInt("hijri_adj", 0)

    private fun daysText(n: Int): String = when (n) {
        0 -> "aaj"
        1 -> "kal"
        else -> "$n din baad"
    }

    private fun updateCalendar() {
        if (!::calPage.isInitialized) return
        val adj = hijriAdj()
        val now = Calendar.getInstance()
        val en = Locale.ENGLISH
        val td = IslamicCalendar.today(adj)
        calPage.set(
            now.get(Calendar.DAY_OF_MONTH),
            SimpleDateFormat("MMM", en).format(now.time).uppercase(),
            SimpleDateFormat("EEE", en).format(now.time).uppercase(),
            td.hijri.day,
            ThemeManager.accent
        )
        calHijri.text = td.hijri.text()
        calGreg.text = SimpleDateFormat("EEEE, d MMM yyyy", en).format(now.time)

        val majors = td.events.filter { it.major }
        val shown = if (majors.isNotEmpty()) majors else td.events
        if (shown.isEmpty()) {
            calEvent.text = "Aaj koi khas din nahi"
            calEvent.setTextColor(0xCCFFFFFF.toInt())
        } else {
            calEvent.text = (if (shown[0].islamic) "\uD83C\uDF19 " else "\u2605 ") + shown.joinToString(" \u2022 ") { it.name }
            calEvent.setTextColor(
                if (majors.isNotEmpty()) ColorUtils.blendARGB(ThemeManager.accent, Color.WHITE, 0.45f) else Color.WHITE
            )
        }
        val nx = IslamicCalendar.nextMajor(adj)
        calNext.text = if (nx != null) {
            "Agla: " + nx.events.joinToString(" \u2022 ") { it.name } + " - " + daysText(nx.daysAhead)
        } else {
            ""
        }
        handler.removeCallbacks(calendarRunnable)
        if (visible) handler.postDelayed(calendarRunnable, 60_000L)
    }

    private fun showCalendarDialog() {
        val adj = hijriAdj()
        val fmt = SimpleDateFormat("EEE, d MMM", Locale.ENGLISH)
        val list = IslamicCalendar.upcoming(adj, 14)
        val items = list.map { u ->
            fmt.format(u.cal.time) + "  (" + u.hijri.day + " " + IslamicCalendar.monthNames[u.hijri.month - 1] + ")\n" +
                u.events.joinToString(" \u2022 ") { it.name } + "  -  " + daysText(u.daysAhead)
        }
        AlertDialog.Builder(this)
            .setTitle("Islamic calendar - khas din")
            .setItems(items.toTypedArray()) { _, _ -> }
            .setPositiveButton("Hijri date theek karein") { _, _ -> showHijriAdjustDialog() }
            .setNegativeButton("Band", null)
            .show()
    }

    private fun showHijriAdjustDialog() {
        val opts = arrayOf("-2 din", "-1 din (chand der se dikhe)", "0 (default)", "+1 din", "+2 din")
        AlertDialog.Builder(this)
            .setTitle("Hijri date adjust (chand ke hisaab se)")
            .setSingleChoiceItems(opts, hijriAdj() + 2) { dlg, which ->
                prefs.edit().putInt("hijri_adj", which - 2).apply()
                updateCalendar()
                dlg.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ================================================================ widgets (show / hide / resize)

    private fun widgetVisible(key: String): Boolean = prefs.getBoolean("w_${key}_on", true)

    private fun widgetSize(key: String): Int = prefs.getInt("w_${key}_size", 1).coerceIn(0, 2)

    private fun applyWidgets() {
        var any = false
        for ((key, id) in widgetIds) {
            val v = findViewById<View>(id)
            val on = widgetVisible(key)
            v.visibility = if (on) View.VISIBLE else View.GONE
            val lp = v.layoutParams as LinearLayout.LayoutParams
            lp.weight = sizeWeights[widgetSize(key)]
            lp.topMargin = if (on && any) dpi(8) else 0
            v.layoutParams = lp
            if (on) any = true
        }
        findViewById<View>(R.id.rightColumn).visibility = if (any) View.VISIBLE else View.GONE
    }

    private fun showWidgetDialog(key: String) {
        val items = arrayOf("Hide / Delete", "Small size", "Normal size", "Large size")
        val checked = if (!widgetVisible(key)) 0 else widgetSize(key) + 1
        AlertDialog.Builder(this)
            .setTitle(widgetTitles[key])
            .setSingleChoiceItems(items, checked) { dlg, which ->
                if (which == 0) {
                    prefs.edit().putBoolean("w_${key}_on", false).apply()
                } else {
                    prefs.edit().putBoolean("w_${key}_on", true).putInt("w_${key}_size", which - 1).apply()
                }
                applyWidgets()
                dlg.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showWidgetsDialog() {
        val keys = widgetIds.keys.toList()
        val items = keys.map { k ->
            widgetTitles[k] + "  -  " + (if (widgetVisible(k)) widgetSizeNames[widgetSize(k)] else "Hidden (add karein)")
        }
        AlertDialog.Builder(this)
            .setTitle("Widgets: add / delete / resize")
            .setItems(items.toTypedArray()) { _, i -> showWidgetDialog(keys[i]) }
            .setNeutralButton("Sab wapas (reset)") { _, _ ->
                for (k in keys) {
                    prefs.edit().putBoolean("w_${k}_on", true).putInt("w_${k}_size", 1).apply()
                }
                applyWidgets()
            }
            .setNegativeButton("Band", null)
            .show()
    }

    // ================================================================ YouTube in PIP

    private fun ytSaved(): List<String> =
        (prefs.getString("yt_saved", "") ?: "").split("\n").filter { it.isNotBlank() }

    private fun ytAdd(spec: String) {
        val list = (listOf(spec) + ytSaved().filter { it != spec }).take(8)
        prefs.edit().putString("yt_saved", list.joinToString("\n")).apply()
    }

    private fun resumeMedia() {
        val saved = prefs.getString("yt_active", null)
        val ref = if (saved != null) YoutubePip.parse(saved) else null
        if (ref != null) startYoutube(ref) else startPip()
    }

    private fun startYoutube(ref: YoutubePip.Ref) {
        stopPip()
        if (!yt.start(ref, !isSound(), Math.round(volume() * 100))) {
            toast("Is device par WebView nahi hai - YouTube PIP nahi chalega")
            prefs.edit().remove("yt_active").apply()
            startPip()
            return
        }
        userPaused = false
        updatePlayIcon()
        setStatus(Status.YOUTUBE)
        channelName.text = "YouTube"
    }

    private fun playYoutube(spec: String) {
        val t = spec.trim()
        val ref = YoutubePip.parse(t)
        if (ref == null) {
            toast("Ye YouTube link samajh nahi aaya")
            return
        }
        ytAdd(t)
        prefs.edit().putString("yt_active", t).apply()
        startYoutube(ref)
    }

    private fun stopYoutubeToTv() {
        yt.stop()
        prefs.edit().remove("yt_active").apply()
        startPip()
    }

    private fun askYoutubeLink() {
        val et = EditText(this)
        et.hint = "https://youtu.be/...  ya  video ID"
        et.setSingleLine(true)
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = cm.primaryClip?.getItemAt(0)?.text?.toString()
            if (clip != null && YoutubePip.parse(clip) != null) et.setText(clip)
        } catch (e: Exception) {
            // clipboard na mile to koi baat nahi
        }
        AlertDialog.Builder(this)
            .setTitle("YouTube link / video ID")
            .setView(et)
            .setPositiveButton("Play") { _, _ -> playYoutube(et.text.toString()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showYoutubeDialog() {
        val saved = ytSaved()
        val items = ArrayList<String>()
        items.add("\u2795  Naya YouTube link / video ID")
        for (sv in saved) items.add("\u25B6  " + sv.take(46))
        if (yt.active) items.add("\u23F9  YouTube band karein (TV par wapas)")
        AlertDialog.Builder(this)
            .setTitle("YouTube in PIP")
            .setItems(items.toTypedArray()) { _, which ->
                when {
                    which == 0 -> askYoutubeLink()
                    which <= saved.size -> playYoutube(saved[which - 1])
                    else -> stopYoutubeToTv()
                }
            }
            .setNegativeButton("Band", null)
            .show()
    }

    private fun openFullscreenPlayer() {
        if (yt.active) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(yt.watchUrl())))
            } catch (e: Exception) {
                toast("YouTube app / browser nahi mila")
            }
        } else {
            startActivity(Intent(this, PlayerActivity::class.java))
        }
    }

    // ================================================================ dialogs

    private fun showSettingsDialog() {
        val sound = isSound()
        val slow = slowMode()
        val items = arrayOf(
            "Channels / Playlist (M3U)",
            "Weather location",
            "Clock style (analog / digital)",
            "Widgets: add / delete / resize",
            "Islamic calendar / Hijri date adjust",
            "Background (picture / colour)",
            "YouTube in PIP (link se)",
            if (slow) "Slow internet mode: ON  (band karein)" else "Slow internet mode: OFF  (on karein)",
            if (sound) "PIP awaaz: ON  (mute karein)" else "PIP awaaz: OFF  (on karein)",
            "Is app ko default launcher banayein"
        )
        AlertDialog.Builder(this)
            .setTitle("Zavi TV Settings")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showChannelMenu()
                    1 -> showCityDialog()
                    2 -> showClockStyleDialog()
                    3 -> showWidgetsDialog()
                    4 -> showCalendarDialog()
                    5 -> showBackgroundDialog()
                    6 -> showYoutubeDialog()
                    7 -> {
                        prefs.edit().putBoolean("slow", !slow).apply()
                        toast(if (!slow) "Slow internet mode ON" else "Slow internet mode OFF")
                        reloadStream()
                    }
                    8 -> toggleMute()
                    9 -> openHomeSettings()
                }
            }
            .show()
    }

    private fun showChannelMenu() {
        val n = Channels.load(prefs).size
        val items = arrayOf(
            if (n > 0) "Channel list dekhein ($n channels)" else "Channel list (abhi khali hai)",
            "Playlist (M3U) link lagayein",
            "Single stream link lagayein",
            "Agla channel",
            "Pichla channel",
            "Awaaz zyada",
            "Awaaz kam",
            "Mute / Unmute",
            "IPTV (server + username + password)"
        )
        AlertDialog.Builder(this)
            .setTitle("Channels")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showChannelList()
                    1 -> showPlaylistDialog()
                    2 -> showStreamDialog()
                    3 -> changeChannel(+1)
                    4 -> changeChannel(-1)
                    5 -> changeVolume(+0.1f)
                    6 -> changeVolume(-0.1f)
                    7 -> toggleMute()
                    8 -> openIptv()
                }
            }
            .show()
    }

    private fun showChannelList() {
        val list = Channels.load(prefs)
        if (list.isEmpty()) {
            toast("Pehle playlist (M3U) link lagayein")
            return
        }
        val names = list.mapIndexed { i, c -> "${i + 1}. ${c.name}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Channels (${list.size})")
            .setSingleChoiceItems(names, Channels.index(prefs, list.size)) { d, which ->
                Channels.select(prefs, which)
                d.dismiss()
                playCurrent()
            }
            .show()
    }

    private fun inputBox(text: String, hint: String, uri: Boolean): Pair<FrameLayout, EditText> {
        val et = EditText(this).apply {
            setText(text)
            this.hint = hint
            setSingleLine(true)
            setSelectAllOnFocus(true)
            inputType = InputType.TYPE_CLASS_TEXT or
                (if (uri) InputType.TYPE_TEXT_VARIATION_URI else 0)
        }
        val pad = dpi(20)
        val box = FrameLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(et, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        return box to et
    }

    private fun showPlaylistDialog() {
        val (box, et) = inputBox(prefs.getString("playlist", "") ?: "", "https://.../playlist.m3u", true)
        AlertDialog.Builder(this)
            .setTitle("Playlist (M3U) link")
            .setView(box)
            .setPositiveButton("Load") { _, _ ->
                val u = et.text.toString().trim()
                if (u.startsWith("http")) loadPlaylist(u) else toast("Sahi http/https link dein")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadPlaylist(url: String) {
        toast("Playlist load ho rahi hai...")
        Thread {
            val list = try {
                Channels.fetch(url)
            } catch (e: Exception) {
                emptyList()
            }
            runOnUiThread {
                if (list.isEmpty()) {
                    toast("Playlist load nahi hui ya usme channel nahi mile")
                } else {
                    prefs.edit().putString("playlist", url).apply()
                    Channels.save(prefs, list)
                    toast("${list.size} channels load ho gaye")
                    reloadStream()
                }
            }
        }.start()
    }

    private fun showStreamDialog() {
        val (box, et) = inputBox(Config.singleUrl(prefs), "https://.../stream.m3u8", true)
        AlertDialog.Builder(this)
            .setTitle("Single stream link (m3u8 / mp4)")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                Channels.clear(prefs)
                val u = et.text.toString().trim()
                if (u.isEmpty()) prefs.edit().remove("stream").apply()
                else prefs.edit().putString("stream", u).apply()
                reloadStream()
            }
            .setNeutralButton("Default") { _, _ ->
                Channels.clear(prefs)
                prefs.edit().remove("stream").apply()
                reloadStream()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCityDialog() {
        val items = (listOf("📍 Current location (auto)", "🔎 Shehar ka naam likhein") +
            WeatherRepo.cities.map { it.name }).toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Weather location")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> useCurrentLocation()
                    1 -> showCitySearchDialog()
                    else -> {
                        saveCity(WeatherRepo.cities[which - 2], false)
                        fetchWeather()
                    }
                }
            }
            .show()
    }

    private fun showCitySearchDialog() {
        val (box, et) = inputBox("", "Shehar / town ka naam (e.g. Okara)", false)
        AlertDialog.Builder(this)
            .setTitle("Shehar dhoondein")
            .setView(box)
            .setPositiveButton("Search") { _, _ ->
                val q = et.text.toString().trim()
                if (q.isEmpty()) return@setPositiveButton
                toast("Dhoondi ja rahi hai...")
                Thread {
                    val c = WeatherRepo.geocode(q)
                    runOnUiThread {
                        if (c == null) {
                            toast("\"$q\" nahi mila")
                        } else {
                            saveCity(c, false)
                            fetchWeather()
                        }
                    }
                }.start()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showInfoDialog() {
        AlertDialog.Builder(this)
            .setTitle("Zavi TV Launcher v1.1")
            .setMessage(
                "• PIP TV par mouse le jayein = Prev / Next / Volume / Mute buttons\n" +
                    "• PIP TV par click = full screen, long-press = Channels menu\n" +
                    "• PIP par LEFT/RIGHT ya remote ke CH+/CH- se channel badlein\n" +
                    "• Theme Color se 10 glass colours, Size (−/+) se PIP chhota/bada\n" +
                    "• Weather card = location (current location / shehar), Clock card = clock style\n" +
                    "• WiFi icon = WiFi settings, 'Add App' tile se apni app home par lagayein\n" +
                    "• IPTV tile: server + username + password se Live / Movies / Series (long-press = account badlein)"
            )
            .setPositiveButton("OK", null)
            .setNeutralButton("Default launcher") { _, _ -> openHomeSettings() }
            .show()
    }

    private fun openWifiSettings() {
        try {
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e2: Exception) {
                toast("WiFi settings open nahi hui")
            }
        }
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

    // ================================================================ app helpers

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

    companion object {
        private const val REQ_LOCATION = 101
    }
}
