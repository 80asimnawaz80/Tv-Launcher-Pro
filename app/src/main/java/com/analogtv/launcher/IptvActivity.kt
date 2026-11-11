package com.analogtv.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

/** IPTV browser: Live TV / Movies / Series (Xtream Codes login). */
class IptvActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var xt: Xtream
    private lateinit var catList: ListView
    private lateinit var itemList: ListView
    private lateinit var status: TextView
    private val catAdapter = RowAdapter()
    private val itemAdapter = RowAdapter()
    private val tabs = ArrayList<TextView>()
    private val cache = HashMap<String, List<XtItem>>()

    private var kind = Kind.LIVE
    private var cats: List<Pair<String, String>> = emptyList()
    private var items: List<XtItem> = emptyList()
    private var shown: List<XtItem> = emptyList()
    private var query = ""
    private var alive = true

    private val dm: Float get() = resources.displayMetrics.density
    private fun dpi(v: Int) = (v * dm).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("analogtv", Context.MODE_PRIVATE)
        ThemeManager.load(prefs)
        val host = prefs.getString("xt_host", "") ?: ""
        val user = prefs.getString("xt_user", "") ?: ""
        val pass = prefs.getString("xt_pass", "") ?: ""
        if (host.isBlank()) {
            finish()
            return
        }
        xt = Xtream(host, user, pass)
        buildUi()
        hideSystemUi()
        selectTab(Kind.LIVE)
    }

    override fun onDestroy() {
        alive = false
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    // ------------------------------------------------------------------ UI

    private fun button(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 18f
        setTextColor(Color.WHITE)
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER
        setPadding(dpi(20), dpi(10), dpi(20), dpi(10))
        isClickable = true
        isFocusable = true
        background = ThemeManager.selector(this@IptvActivity, 22f)
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = dpi(10) }
    }

    private fun makeList(): ListView = ListView(this).apply {
        divider = null
        dividerHeight = dpi(6)
        selector = ColorDrawable(Color.TRANSPARENT)
        overScrollMode = View.OVER_SCROLL_NEVER
        setPadding(dpi(8), dpi(8), dpi(8), dpi(8))
        clipToPadding = false
        background = GradientDrawable().apply {
            setColor(0x33000000)
            cornerRadius = dpi(18).toFloat()
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ThemeManager.background()
            setPadding(dpi(28), dpi(18), dpi(28), dpi(14))
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(
            TextView(this).apply {
                text = "Zavi IPTV"
                textSize = 28f
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        val labels = mapOf(Kind.LIVE to "Live TV", Kind.MOVIE to "Movies", Kind.SERIES to "Series")
        for (k in Kind.values()) {
            val b = button(labels[k] ?: k.name) { selectTab(k) }
            tabs.add(b)
            top.addView(b)
        }
        top.addView(button("Search") { showSearch() })
        top.addView(button("Logout") { logout() })

        catList = makeList()
        itemList = makeList()
        catList.adapter = catAdapter
        itemList.adapter = itemAdapter
        catList.setOnItemClickListener { _, _, pos, _ -> openCategory(pos) }
        itemList.setOnItemClickListener { _, _, pos, _ -> openItem(pos) }

        val body = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        body.addView(
            catList,
            LinearLayout.LayoutParams(dpi(300), ViewGroup.LayoutParams.MATCH_PARENT).apply {
                topMargin = dpi(12)
                marginEnd = dpi(12)
            }
        )
        body.addView(
            itemList,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { topMargin = dpi(12) }
        )

        status = TextView(this).apply {
            textSize = 14f
            setTextColor(0xCCFFFFFF.toInt())
            setPadding(dpi(6), dpi(8), 0, 0)
        }

        root.addView(top)
        root.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(status)
        setContentView(FrameLayout(this).apply { addView(root) })
    }

    private inner class RowAdapter : BaseAdapter() {
        var data: List<String> = emptyList()
        override fun getCount() = data.size
        override fun getItem(position: Int) = data[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val tv = (convertView as? TextView) ?: TextView(this@IptvActivity).apply {
                textSize = 18f
                setTextColor(Color.WHITE)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dpi(16), 0, dpi(16), 0)
                background = ThemeManager.selector(this@IptvActivity, 14f)
                layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpi(50))
            }
            tv.text = data[position]
            return tv
        }
    }

    private fun setStatus(s: String) {
        status.text = s
    }

    // ------------------------------------------------------------------ data

    private fun selectTab(k: Kind) {
        kind = k
        query = ""
        cats = emptyList()
        items = emptyList()
        shown = emptyList()
        catAdapter.data = emptyList()
        itemAdapter.data = emptyList()
        catAdapter.notifyDataSetChanged()
        itemAdapter.notifyDataSetChanged()
        Kind.values().forEachIndexed { i, kk -> tabs[i].isSelected = kk == k }
        loadCategories()
    }

    private fun loadCategories() {
        val k = kind
        setStatus("Categories load ho rahi hain...")
        Thread {
            val r = try {
                xt.categories(k)
            } catch (e: Exception) {
                null
            }
            runOnUiThread {
                if (!alive || k != kind) return@runOnUiThread
                if (r == null) {
                    setStatus("Load nahi hua - internet ya login check karein")
                } else {
                    cats = r
                    catAdapter.data = r.map { it.second }
                    catAdapter.notifyDataSetChanged()
                    setStatus("${r.size} categories - ek category chunein")
                    catList.requestFocus()
                }
            }
        }.start()
    }

    private fun openCategory(pos: Int) {
        if (pos !in cats.indices) return
        val k = kind
        val id = cats[pos].first
        val key = "${k.name}:$id"
        val hit = cache[key]
        if (hit != null) {
            showItems(hit)
            return
        }
        setStatus("${cats[pos].second} load ho rahi hai...")
        Thread {
            val r = try {
                xt.items(k, id)
            } catch (e: Exception) {
                null
            }
            runOnUiThread {
                if (!alive || k != kind) return@runOnUiThread
                if (r == null) {
                    setStatus("Load nahi hua - dobara koshish karein")
                } else {
                    cache[key] = r
                    showItems(r)
                }
            }
        }.start()
    }

    private fun showItems(list: List<XtItem>) {
        items = list
        query = ""
        applyFilter()
        itemList.requestFocus()
        if (shown.isNotEmpty()) itemList.setSelection(0)
    }

    private fun applyFilter() {
        shown = if (query.isBlank()) items else items.filter { it.name.contains(query, ignoreCase = true) }
        itemAdapter.data = shown.map { it.name }
        itemAdapter.notifyDataSetChanged()
        setStatus(
            if (query.isBlank()) "${shown.size} items"
            else "${shown.size} items  (search: \"$query\")"
        )
    }

    private fun openItem(pos: Int) {
        if (pos !in shown.indices) return
        val item = shown[pos]
        when (kind) {
            Kind.LIVE -> {
                // Is category ke saare channels list me save: home PIP aur fullscreen me CH+/CH- se badal sakte hain
                Channels.save(prefs, shown.map { Channel(it.name, xt.liveUrl(it.id)) })
                Channels.select(prefs, pos)
                startActivity(Intent(this, PlayerActivity::class.java))
            }
            Kind.MOVIE -> play(item.name, xt.movieUrl(item.id, item.ext))
            Kind.SERIES -> showEpisodes(item)
        }
    }

    private fun play(title: String, url: String) {
        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_URL, url)
                .putExtra(PlayerActivity.EXTRA_TITLE, title)
        )
    }

    private fun showEpisodes(series: XtItem) {
        setStatus("${series.name}: episodes load ho rahi hain...")
        Thread {
            val eps = try {
                xt.episodes(series.id)
            } catch (e: Exception) {
                null
            }
            runOnUiThread {
                if (!alive) return@runOnUiThread
                applyFilterStatus()
                if (eps == null) {
                    toast("Episodes load nahi huin")
                } else if (eps.isEmpty()) {
                    toast("Is series me episodes nahi mili")
                } else {
                    AlertDialog.Builder(this)
                        .setTitle(series.name)
                        .setItems(eps.map { it.first }.toTypedArray()) { _, which ->
                            play("${series.name} - ${eps[which].first}", eps[which].second)
                        }
                        .show()
                }
            }
        }.start()
    }

    private fun applyFilterStatus() {
        setStatus(
            if (query.isBlank()) "${shown.size} items"
            else "${shown.size} items  (search: \"$query\")"
        )
    }

    private fun showSearch() {
        if (items.isEmpty()) {
            toast("Pehle koi category kholein")
            return
        }
        val et = EditText(this).apply {
            setText(query)
            hint = "Naam likhein"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val pad = dpi(20)
        val box = FrameLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(et, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        AlertDialog.Builder(this)
            .setTitle("Is category me search")
            .setView(box)
            .setPositiveButton("Search") { _, _ ->
                query = et.text.toString().trim()
                applyFilter()
                itemList.requestFocus()
            }
            .setNeutralButton("Clear") { _, _ ->
                query = ""
                applyFilter()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun logout() {
        prefs.edit().remove("xt_host").remove("xt_user").remove("xt_pass").apply()
        toast("IPTV logout ho gaya")
        finish()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
