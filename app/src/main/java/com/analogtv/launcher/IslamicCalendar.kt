package com.analogtv.launcher

import java.util.Calendar

/**
 * Islamic (Hijri) calendar - offline. Tabular algorithm hai, isliye chand ke hisaab se
 * +/-1 din ka farq ho sakta hai: Settings me "Hijri date adjust" se theek kar lein.
 * Islamic khas din + Pakistan ke qaumi din + Jumu'ah / Ayyam al-Bidh.
 */
object IslamicCalendar {

    val monthNames = arrayOf(
        "Muharram", "Safar", "Rabi al-Awwal", "Rabi al-Thani", "Jumada al-Awwal", "Jumada al-Thani",
        "Rajab", "Shaban", "Ramadan", "Shawwal", "Dhul Qadah", "Dhul Hijjah"
    )

    class HDate(val year: Int, val month: Int, val day: Int) {
        fun text(): String = "$day ${monthNames[month - 1]} $year AH"
    }

    /** major = khas din, !major = rozana wali yaad-dehani (Jumu'ah, Roza, Ayyam al-Bidh). */
    class Event(val name: String, val islamic: Boolean, val major: Boolean)

    class Today(val hijri: HDate, val events: List<Event>)

    class Upcoming(val daysAhead: Int, val cal: Calendar, val hijri: HDate, val events: List<Event>)

    // key = month * 100 + day
    private val islamicDays = mapOf(
        101 to "Islamic New Year (1 Muharram)",
        109 to "Tasua (9 Muharram)",
        110 to "Ashura (10 Muharram)",
        312 to "Eid Milad-un-Nabi (12 Rabi al-Awwal)",
        411 to "Gyarhvin Sharif (11 Rabi al-Thani)",
        701 to "Rajab shuru - Hurmat wala mahina",
        727 to "Shab-e-Meraj (27 Rajab)",
        815 to "Shab-e-Barat (15 Shaban)",
        901 to "Ramadan shuru - Pehla Roza",
        917 to "Youm-e-Badr (17 Ramadan)",
        921 to "Shab-e-Qadr / Youm-e-Ali (21 Ramadan)",
        923 to "Shab-e-Qadr (23 Ramadan)",
        925 to "Shab-e-Qadr (25 Ramadan)",
        927 to "Shab-e-Qadr (27 Ramadan)",
        929 to "Shab-e-Qadr (29 Ramadan)",
        1001 to "Eid al-Fitr",
        1002 to "Eid al-Fitr (2nd day)",
        1003 to "Eid al-Fitr (3rd day)",
        1201 to "Dhul Hijjah shuru - pehle 10 din",
        1209 to "Youm-e-Arafah (9 Dhul Hijjah)",
        1210 to "Eid al-Adha",
        1211 to "Eid al-Adha (2nd day)",
        1212 to "Eid al-Adha (3rd day)"
    )

    // key = month * 100 + day (English calendar)
    private val worldDays = mapOf(
        101 to "New Year's Day",
        205 to "Kashmir Solidarity Day",
        323 to "Pakistan Day",
        501 to "Labour Day",
        528 to "Youm-e-Takbeer",
        814 to "Independence Day (Pakistan)",
        906 to "Defence Day",
        911 to "Quaid-e-Azam Death Anniversary",
        1109 to "Iqbal Day",
        1225 to "Quaid-e-Azam Day"
    )

    private fun jdn(y: Int, m: Int, d: Int): Int {
        val a = (14 - m) / 12
        val yy = y + 4800 - a
        val mm = m + 12 * a - 3
        return d + (153 * mm + 2) / 5 + 365 * yy + yy / 4 - yy / 100 + yy / 400 - 32045
    }

    fun toHijri(y: Int, m: Int, d: Int, adj: Int): HDate {
        var l = jdn(y, m, d) + adj - 1948440 + 10632
        val n = (l - 1) / 10631
        l = l - 10631 * n + 354
        val j = ((10985 - l) / 5316) * ((50 * l) / 17719) + (l / 5670) * ((43 * l) / 15238)
        l = l - ((30 - j) / 15) * ((17719 * j) / 50) - (j / 16) * ((15238 * j) / 43) + 29
        val mo = (24 * l) / 709
        val dd = l - (709 * mo) / 24
        val yr = 30 * n + j - 30
        return HDate(yr, mo.coerceIn(1, 12), dd)
    }

    private fun hijriOf(cal: Calendar, adj: Int): HDate =
        toHijri(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH), adj)

    private fun eventsFor(cal: Calendar, h: HDate): List<Event> {
        val out = ArrayList<Event>()
        islamicDays[h.month * 100 + h.day]?.let { out.add(Event(it, true, true)) }
        worldDays[(cal.get(Calendar.MONTH) + 1) * 100 + cal.get(Calendar.DAY_OF_MONTH)]?.let {
            out.add(Event(it, false, true))
        }
        if (h.month == 9 && out.none { it.major }) {
            out.add(Event("Ramadan Mubarak - Roza ${h.day}", true, false))
        } else if (h.month != 9 && h.day in 13..15) {
            out.add(Event("Ayyam al-Bidh - nafil roza (${h.day} ${monthNames[h.month - 1]})", true, false))
        }
        if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY) {
            out.add(Event("Jumu'ah Mubarak", true, false))
        }
        return out
    }

    fun today(adj: Int): Today {
        val cal = Calendar.getInstance()
        val h = hijriOf(cal, adj)
        return Today(h, eventsFor(cal, h))
    }

    /** Aaj se aage ke khas (major) din. from = 0 means aaj bhi shamil. */
    fun upcoming(adj: Int, limit: Int, from: Int = 0, maxDays: Int = 420): List<Upcoming> {
        val out = ArrayList<Upcoming>()
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, from)
        var i = from
        while (i <= maxDays && out.size < limit) {
            val h = hijriOf(cal, adj)
            val ev = eventsFor(cal, h).filter { it.major }
            if (ev.isNotEmpty()) out.add(Upcoming(i, cal.clone() as Calendar, h, ev))
            cal.add(Calendar.DAY_OF_YEAR, 1)
            i++
        }
        return out
    }

    fun nextMajor(adj: Int): Upcoming? = upcoming(adj, 1, 1).firstOrNull()
}
