package com.analogtv.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Phone / box me installed saari apps ki grid. Click = app khulti hai. Back = wapas. */
class AllAppsActivity : Activity() {

    private class Entry(val label: String, val icon: Drawable, val intent: Intent)

    private lateinit var grid: GridView
    private var entries: List<Entry> = emptyList()

    private val dm: Float get() = resources.displayMetrics.density
    private fun dpi(v: Int) = (v * dm).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("analogtv", Context.MODE_PRIVATE)
        ThemeManager.load(prefs)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ThemeManager.background()
            setPadding(dpi(32), dpi(20), dpi(32), dpi(16))
        }
        root.addView(TextView(this).apply {
            text = "All Apps"
            setTextColor(Color.WHITE)
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dpi(12))
        })

        grid = GridView(this).apply {
            numColumns = 6
            verticalSpacing = dpi(10)
            horizontalSpacing = dpi(10)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            selector = ColorDrawable(Color.TRANSPARENT)
            overScrollMode = View.OVER_SCROLL_NEVER
            onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ -> launch(pos) }
        }
        root.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        Thread {
            val pm = packageManager
            val seen = HashSet<String>()
            val list = ArrayList<Entry>()
            for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
                val q = Intent(Intent.ACTION_MAIN).addCategory(cat)
                for (ri in pm.queryIntentActivities(q, 0)) {
                    val pkg = ri.activityInfo.packageName
                    if (pkg == packageName || !seen.add(pkg)) continue
                    val intent = Intent(Intent.ACTION_MAIN)
                        .addCategory(cat)
                        .setClassName(pkg, ri.activityInfo.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    list.add(Entry(ri.loadLabel(pm).toString(), ri.loadIcon(pm), intent))
                }
            }
            list.sortBy { it.label.lowercase() }
            runOnUiThread {
                entries = list
                grid.adapter = AppAdapter()
                grid.requestFocus()
            }
        }.start()
    }

    private fun launch(pos: Int) {
        if (pos !in entries.indices) return
        try {
            startActivity(entries[pos].intent)
        } catch (e: Exception) {
            Toast.makeText(this, "App khul nahi saki", Toast.LENGTH_SHORT).show()
        }
    }

    private inner class AppAdapter : BaseAdapter() {
        override fun getCount() = entries.size
        override fun getItem(position: Int): Any = entries[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val e = entries[position]
            val ctx = this@AllAppsActivity
            val tile = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dpi(6), dpi(10), dpi(6), dpi(8))
                background = ThemeManager.selector(ctx, 18f)
            }
            tile.addView(ImageView(ctx).apply {
                setImageDrawable(e.icon)
                layoutParams = LinearLayout.LayoutParams(dpi(56), dpi(56))
            })
            tile.addView(TextView(ctx).apply {
                text = e.label
                setTextColor(Color.WHITE)
                textSize = 12f
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dpi(6), 0, 0)
            })
            return tile
        }
    }
}
