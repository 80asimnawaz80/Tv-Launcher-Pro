package com.analogtv.launcher

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class City(val name: String, val lat: Double, val lon: Double)
data class Weather(val icon: String, val temp: Int, val desc: String)
data class IpLoc(val name: String?, val lat: Double, val lon: Double)

/** Weather (Open-Meteo), shehar ki talash aur location (IP / reverse) - sab free, bina API key. */
object WeatherRepo {

    val cities = listOf(
        City("Lahore", 31.5204, 74.3587),
        City("Karachi", 24.8607, 67.0011),
        City("Islamabad", 33.6844, 73.0479),
        City("Rawalpindi", 33.5651, 73.0169),
        City("Faisalabad", 31.4504, 73.1350),
        City("Multan", 30.1575, 71.5249),
        City("Peshawar", 34.0151, 71.5249),
        City("Quetta", 30.1798, 66.9750),
        City("Okara", 30.8138, 73.4534),
        City("Dubai", 25.2048, 55.2708),
        City("Riyadh", 24.7136, 46.6753),
        City("London", 51.5072, -0.1276),
        City("New York", 40.7128, -74.0060)
    )

    private fun httpGet(url: String): String? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 12000
        c.readTimeout = 15000
        c.setRequestProperty("User-Agent", "ZaviTV/1.1")
        try {
            if (c.responseCode in 200..299) c.inputStream.bufferedReader().use { it.readText() } else null
        } finally {
            c.disconnect()
        }
    } catch (e: Exception) {
        null
    }

    private fun describe(code: Int): Pair<String, String> = when (code) {
        0 -> "☀️" to "Clear"
        1, 2 -> "🌤️" to "Partly cloudy"
        3 -> "☁️" to "Cloudy"
        45, 48 -> "🌫️" to "Fog"
        51, 53, 55, 56, 57 -> "🌦️" to "Drizzle"
        61, 63, 65, 66, 67, 80, 81, 82 -> "🌧️" to "Rain"
        71, 73, 75, 77, 85, 86 -> "❄️" to "Snow"
        95, 96, 99 -> "⛈️" to "Thunderstorm"
        else -> "🌡️" to "Weather"
    }

    fun fetch(city: City): Weather? {
        val body = httpGet(
            "https://api.open-meteo.com/v1/forecast?latitude=${city.lat}&longitude=${city.lon}" +
                "&current=temperature_2m,weather_code&timezone=auto"
        ) ?: return null
        return try {
            val cur = JSONObject(body).getJSONObject("current")
            val t = Math.round(cur.getDouble("temperature_2m")).toInt()
            val d = describe(cur.getInt("weather_code"))
            Weather(d.first, t, d.second)
        } catch (e: Exception) {
            null
        }
    }

    /** Shehar ka naam se lat/lon dhoondein. */
    fun geocode(q: String): City? {
        val body = httpGet(
            "https://geocoding-api.open-meteo.com/v1/search?count=1&language=en&name=" +
                URLEncoder.encode(q, "UTF-8")
        ) ?: return null
        return try {
            val arr = JSONObject(body).optJSONArray("results")
            if (arr == null || arr.length() == 0) {
                null
            } else {
                val o = arr.getJSONObject(0)
                City(o.getString("name"), o.getDouble("latitude"), o.getDouble("longitude"))
            }
        } catch (e: Exception) {
            null
        }
    }

    /** GPS na mile to internet (IP) se andaza. */
    fun ipLocation(): IpLoc? {
        val body = httpGet("https://ipwho.is/") ?: return null
        return try {
            val o = JSONObject(body)
            if (!o.optBoolean("success", false)) {
                null
            } else {
                val city = o.optString("city", "")
                IpLoc(if (city.isEmpty()) null else city, o.getDouble("latitude"), o.getDouble("longitude"))
            }
        } catch (e: Exception) {
            null
        }
    }

    /** lat/lon se shehar ka naam. */
    fun reverseName(lat: Double, lon: Double): String? {
        val body = httpGet(
            "https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=$lat&longitude=$lon&localityLanguage=en"
        ) ?: return null
        return try {
            val o = JSONObject(body)
            var n = o.optString("city", "")
            if (n.isEmpty()) n = o.optString("locality", "")
            if (n.isEmpty()) null else n
        } catch (e: Exception) {
            null
        }
    }
}
