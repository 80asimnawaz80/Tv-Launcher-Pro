package com.analogtv.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.SharedPreferences
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

enum class Kind(val catAction: String, val listAction: String) {
    LIVE("get_live_categories", "get_live_streams"),
    MOVIE("get_vod_categories", "get_vod_streams"),
    SERIES("get_series_categories", "get_series")
}

data class XtItem(val id: String, val name: String, val ext: String)
data class XtLink(val host: String, val user: String, val pass: String)

/** Xtream Codes API client: server + username + password se Live / Movies / Series. */
class Xtream(val host: String, val user: String, val pass: String) {

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun httpGet(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 45000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) ZaviTV")
        try {
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun api(action: String?, extra: String = ""): String {
        var u = "$host/player_api.php?username=${enc(user)}&password=${enc(pass)}"
        if (action != null) u += "&action=$action"
        return httpGet(u + extra)
    }

    /** null = login theek, warna error message. */
    fun checkLogin(): String? {
        try {
            val o = JSONObject(api(null))
            val ui = o.optJSONObject("user_info") ?: return "Server ne account info nahi diya"
            if (ui.optString("auth") != "1") return "Username ya password ghalat hai"
            val st = ui.optString("status")
            if (st.isNotEmpty() && !st.equals("Active", ignoreCase = true)) return "Account $st hai"
            return null
        } catch (e: Exception) {
            return "Server se connect nahi hua (${e.javaClass.simpleName})"
        }
    }

    fun categories(k: Kind): List<Pair<String, String>> {
        val a = JSONArray(api(k.catAction))
        val out = ArrayList<Pair<String, String>>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            out.add(o.optString("category_id") to o.optString("category_name"))
        }
        return out
    }

    fun items(k: Kind, categoryId: String): List<XtItem> {
        val a = JSONArray(api(k.listAction, "&category_id=" + enc(categoryId)))
        val out = ArrayList<XtItem>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val id = if (k == Kind.SERIES) o.optString("series_id") else o.optString("stream_id")
            if (id.isBlank()) continue
            val ext = o.optString("container_extension", "mp4").ifBlank { "mp4" }
            out.add(XtItem(id, o.optString("name", "?"), ext))
        }
        return out
    }

    /** Series ki saari episodes: (label, url). */
    fun episodes(seriesId: String): List<Pair<String, String>> {
        val o = JSONObject(api("get_series_info", "&series_id=" + enc(seriesId)))
        val out = ArrayList<Pair<String, String>>()
        fun addSeason(season: String, arr: JSONArray?) {
            if (arr == null) return
            for (i in 0 until arr.length()) {
                val e = arr.optJSONObject(i) ?: continue
                val id = e.optString("id")
                if (id.isBlank()) continue
                val ext = e.optString("container_extension", "mp4").ifBlank { "mp4" }
                val num = e.optInt("episode_num", i + 1)
                val title = e.optString("title")
                out.add("S$season E$num  $title".trim() to "$host/series/${enc(user)}/${enc(pass)}/$id.$ext")
            }
        }
        when (val eps = o.opt("episodes")) {
            is JSONObject -> {
                val keys = eps.keys().asSequence().toList().sortedBy { it.toIntOrNull() ?: 0 }
                for (key in keys) addSeason(key, eps.optJSONArray(key))
            }
            is JSONArray -> for (i in 0 until eps.length()) addSeason((i + 1).toString(), eps.optJSONArray(i))
            else -> {}
        }
        return out
    }

    fun liveUrl(id: String) = "$host/live/${enc(user)}/${enc(pass)}/$id.m3u8"
    fun movieUrl(id: String, ext: String) = "$host/movie/${enc(user)}/${enc(pass)}/$id.$ext"

    companion object {
        fun normalizeHost(input: String): String {
            var h = input.trim()
            if (!h.contains("://")) h = "http://$h"
            h = h.substringBefore("/player_api.php").substringBefore("/get.php")
            return h.trimEnd('/')
        }

        /** Poora get.php?username=..&password=.. link paste ho to usse host/user/pass nikalta hai. */
        fun parseLink(s: String): XtLink? {
            if (!s.contains("username=") || !s.contains("password=")) return null
            return try {
                val u = URL(if (s.contains("://")) s.trim() else "http://" + s.trim())
                val q = u.query ?: return null
                val map = HashMap<String, String>()
                for (part in q.split("&")) {
                    val kv = part.split("=", limit = 2)
                    if (kv.size == 2) map[kv[0]] = java.net.URLDecoder.decode(kv[1], "UTF-8")
                }
                val user = map["username"] ?: return null
                val pass = map["password"] ?: return null
                XtLink("${u.protocol}://${u.authority}", user, pass)
            } catch (e: Exception) {
                null
            }
        }
    }
}

/** Login dialog (server, username, password). Kamyabi par prefs me save karke onDone() chalata hai. */
fun Activity.showXtreamLogin(prefs: SharedPreferences, onDone: () -> Unit) {
    val d = resources.displayMetrics.density
    fun field(hint: String, text: String, kind: Int) = EditText(this).apply {
        this.hint = hint
        setText(text)
        setSingleLine(true)
        inputType = InputType.TYPE_CLASS_TEXT or kind
    }
    val host = field(
        "Server (http://example.com:8080) ya poora get.php link",
        prefs.getString("xt_host", "") ?: "", InputType.TYPE_TEXT_VARIATION_URI
    )
    val user = field("Username", prefs.getString("xt_user", "") ?: "", InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
    val pass = field("Password", prefs.getString("xt_pass", "") ?: "", InputType.TYPE_TEXT_VARIATION_PASSWORD)
    val pad = (20 * d).toInt()
    val box = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(pad, pad / 2, pad, 0)
        addView(host)
        addView(user)
        addView(pass)
    }
    AlertDialog.Builder(this)
        .setTitle("IPTV login")
        .setView(box)
        .setPositiveButton("Login") { _, _ ->
            var h = host.text.toString().trim()
            var u = user.text.toString().trim()
            var p = pass.text.toString().trim()
            val link = Xtream.parseLink(h)
            if (link != null) {
                h = link.host
                if (u.isEmpty()) u = link.user
                if (p.isEmpty()) p = link.pass
            }
            if (h.isEmpty() || u.isEmpty() || p.isEmpty()) {
                Toast.makeText(this, "Server, username aur password teeno likhein", Toast.LENGTH_LONG).show()
            } else {
                val x = Xtream(Xtream.normalizeHost(h), u, p)
                Toast.makeText(this, "Login check ho raha hai...", Toast.LENGTH_SHORT).show()
                Thread {
                    val err = x.checkLogin()
                    runOnUiThread {
                        if (err == null) {
                            prefs.edit()
                                .putString("xt_host", x.host)
                                .putString("xt_user", x.user)
                                .putString("xt_pass", x.pass)
                                .apply()
                            Toast.makeText(this, "Login kamyab", Toast.LENGTH_SHORT).show()
                            onDone()
                        } else {
                            Toast.makeText(this, "Login nahi hua: $err", Toast.LENGTH_LONG).show()
                        }
                    }
                }.start()
            }
        }
        .setNegativeButton("Cancel", null)
        .show()
}
