package com.analogtv.launcher

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class City(val name: String, val lat: Double, val lon: Double)
data class Weather(val temp: Int, val desc: String, val icon: String)

object WeatherRepo {
    val cities = listOf(
        City("Islamabad", 33.6844, 73.0479),
        City("Rawalpindi", 33.5651, 73.0169),
        City("Lahore", 31.5204, 74.3587),
        City("Karachi", 24.8607, 67.0011),
        City("Peshawar", 34.0151, 71.5249),
        City("Quetta", 30.1798, 66.9750),
        City("Multan", 30.1575, 71.5249),
        City("Faisalabad", 31.4504, 73.1350)
    )

    // Open-Meteo: free, no API key
    fun fetch(c: City): Weather? = try {
        val url = URL(
            "https://api.open-meteo.com/v1/forecast?latitude=${c.lat}&longitude=${c.lon}" +
                "&current=temperature_2m,weather_code&timezone=auto"
        )
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        val text = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        val cur = JSONObject(text).getJSONObject("current")
        val t = Math.round(cur.getDouble("temperature_2m")).toInt()
        describe(cur.getInt("weather_code"), t)
    } catch (e: Exception) {
        null
    }

    private fun describe(code: Int, t: Int): Weather = when (code) {
        0 -> Weather(t, "Clear Sky", "☀️")
        1 -> Weather(t, "Mainly Clear", "🌤️")
        2 -> Weather(t, "Partly Cloudy", "⛅")
        3 -> Weather(t, "Overcast", "☁️")
        45, 48 -> Weather(t, "Foggy", "🌫️")
        in 51..57 -> Weather(t, "Drizzle", "🌦️")
        in 61..67 -> Weather(t, "Rain", "🌧️")
        in 71..77 -> Weather(t, "Snow", "❄️")
        in 80..82 -> Weather(t, "Rain Showers", "🌧️")
        85, 86 -> Weather(t, "Snow Showers", "🌨️")
        in 95..99 -> Weather(t, "Thunderstorm", "⛈️")
        else -> Weather(t, "Cloudy", "☁️")
    }
}
