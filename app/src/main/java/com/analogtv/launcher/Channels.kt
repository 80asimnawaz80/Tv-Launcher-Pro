package com.analogtv.launcher

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Channel(val name: String, val url: String)

/** Channel list (M3U playlist / IPTV category) jo SharedPreferences me save hoti hai. */
object Channels {
    private const val KEY_LIST = "channels_json"
    private const val KEY_INDEX = "channel_index"

    @Volatile private var cacheRaw: String? = null
    @Volatile private var cacheList: List<Channel> = emptyList()

    fun load(prefs: SharedPreferences): List<Channel> {
        val raw = prefs.getString(KEY_LIST, null)
        if (raw.isNullOrEmpty()) return emptyList()
        if (raw == cacheRaw) return cacheList
        val list = ArrayList<Channel>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val u = o.optString("u")
                if (u.isNotEmpty()) list.add(Channel(o.optString("n"), u))
            }
        } catch (e: Exception) {
            // kharab data: khali list
        }
        cacheRaw = raw
        cacheList = list
        return list
    }

    fun save(prefs: SharedPreferences, list: List<Channel>) {
        val arr = JSONArray()
        for (c in list) arr.put(JSONObject().put("n", c.name).put("u", c.url))
        prefs.edit().putString(KEY_LIST, arr.toString()).putInt(KEY_INDEX, 0).apply()
    }

    fun clear(prefs: SharedPreferences) {
        prefs.edit().remove(KEY_LIST).remove(KEY_INDEX).apply()
        cacheRaw = null
        cacheList = emptyList()
    }

    fun index(prefs: SharedPreferences, size: Int): Int {
        if (size <= 0) return 0
        return prefs.getInt(KEY_INDEX, 0).coerceIn(0, size - 1)
    }

    fun select(prefs: SharedPreferences, i: Int) {
        prefs.edit().putInt(KEY_INDEX, i).apply()
    }

    fun current(prefs: SharedPreferences): Channel? {
        val l = load(prefs)
        return if (l.isEmpty()) null else l[index(prefs, l.size)]
    }

    /** delta (+1 / -1) se agla / pichla channel; list me 2 se kam channel hon to null. */
    fun step(prefs: SharedPreferences, delta: Int): Channel? {
        val l = load(prefs)
        if (l.size < 2) return null
        val n = ((index(prefs, l.size) + delta) % l.size + l.size) % l.size
        select(prefs, n)
        return l[n]
    }

    /** M3U playlist link se channels download karein (background thread me chalayein). */
    fun fetch(url: String): List<Channel> {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) ZaviTV")
        val text = try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
        return parseM3u(text)
    }

    fun parseM3u(text: String): List<Channel> {
        val out = ArrayList<Channel>()
        var name: String? = null
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#EXTINF", true)) {
                val comma = line.lastIndexOf(',')
                var n = if (comma >= 0) line.substring(comma + 1).trim() else ""
                if (n.isEmpty()) {
                    val m = Regex("tvg-name=\"([^\"]*)\"").find(line)
                    n = m?.groupValues?.get(1) ?: ""
                }
                name = n
            } else if (!line.startsWith("#")) {
                val nm = name
                out.add(Channel(if (nm.isNullOrEmpty()) "Channel ${out.size + 1}" else nm, line))
                name = null
            }
        }
        return out
    }
}
