package com.analogtv.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class AllAppsActivity : Activity() {

    private class AppItem(val label: String, val icon: Drawable, val pkg: String)

    private val apps = ArrayList<AppItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("analogtv", Context.MODE_PRIVATE)
        ThemeManager.load(prefs)
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        loadApps()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ThemeManager.background()
            setPadding(dp(32), dp(24), dp(32), dp(16))
        }
        root.addView(TextView(this).apply {
            text = "All Apps"
            textSize = 30f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        val grid = GridView(this).apply {
            numColumns = 6
            verticalSpacing = dp(12)
            horizontalSpacing = dp(12)
            selector = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
            clipToPadding = false
            setPadding(0, dp(16), 0, dp(16))
            adapter = object : BaseAdapter() {
                override fun getCount() = apps.size
                override fun getItem(position: Int) = apps[position]
                override fun getItemId(position: Int) = position.toLong()
                override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                    val item = apps[position]
                    val cell = LinearLayout(this@AllAppsActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER
                        setPadding(dp(8), dp(14), dp(8), dp(10))
                        background = ThemeManager.selector(this@AllAppsActivity, 20f)
                        layoutParams = AbsListViewParams(dp(150))
                    }
                    cell.addView(ImageView(this@AllAppsActivity).apply {
                        setImageDrawable(item.icon)
                        layoutParams = LinearLayout.LayoutParams(dp(64), dp(64))
                    })
                    cell.addView(TextView(this@AllAppsActivity).apply {
                        text = item.label
                        textSize = 14f
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        gravity = Gravity.CENTER
                        setTextColor(Color.WHITE)
                        setPadding(0, dp(8), 0, 0)
                    })
                    return cell
                }
            }
            setOnItemClickListener { _, _, pos, _ -> launch(apps[pos].pkg) }
        }
        root.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        hideSystemUi()
    }

    @Suppress("FunctionName")
    private fun AbsListViewParams(h: Int) =
        android.widget.AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h)

    private fun loadApps() {
        val pm = packageManager
        val found = LinkedHashMap<String, ResolveInfo>()
        for (cat in listOf(Intent.CATEGORY_LAUNCHER, Intent.CATEGORY_LEANBACK_LAUNCHER)) {
            val i = Intent(Intent.ACTION_MAIN).addCategory(cat)
            for (ri in pm.queryIntentActivities(i, 0)) {
                val pkg = ri.activityInfo.packageName
                if (pkg != packageName && !found.containsKey(pkg)) found[pkg] = ri
            }
        }
        apps.clear()
        found.values
            .map { AppItem(it.loadLabel(pm).toString(), it.loadIcon(pm), it.activityInfo.packageName) }
            .sortedBy { it.label.lowercase() }
            .forEach { apps.add(it) }
    }

    private fun launch(pkg: String) {
        val i = packageManager.getLeanbackLaunchIntentForPackage(pkg)
            ?: packageManager.getLaunchIntentForPackage(pkg)
        if (i != null) startActivity(i)
        else Toast.makeText(this, "App open nahi ho saki", Toast.LENGTH_SHORT).show()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }
}
