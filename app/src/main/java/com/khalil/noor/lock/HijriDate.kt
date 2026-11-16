package com.khalil.noor.lock

import android.icu.util.IslamicCalendar
import java.util.Calendar
import java.util.Date

/** تاريخ اليوم بالهجري (تقويم أم القرى) والميلادي، بالعربي. */
data class DayInfo(
    val dayName: String,
    val hijriDay: Int,
    val hijriMonth: String,
    val hijriYear: Int,
    val gregDay: Int,
    val gregMonth: String,
    val gregYear: Int,
    /** مفتاح ثابت لليوم، نستخدمه نعرف هل تغيّر اليوم */
    val key: String,
)

object HijriDate {
    private val HIJRI_MONTHS = arrayOf(
        "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
        "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة",
    )
    private val GREG_MONTHS = arrayOf(
        "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
        "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر",
    )
    private val DAYS = arrayOf("الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت")

    /** [adjust] تصحيح الهجري بيوم زائد أو ناقص لو اختلفت الرؤية */
    fun today(adjust: Int = 0, now: Date = Date()): DayInfo {
        val g = Calendar.getInstance().apply { time = now }

        val h = IslamicCalendar()
        h.calculationType = IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
        h.time = now
        if (adjust != 0) h.add(IslamicCalendar.DAY_OF_MONTH, adjust)

        val hd = h.get(IslamicCalendar.DAY_OF_MONTH)
        val hm = h.get(IslamicCalendar.MONTH)
        val hy = h.get(IslamicCalendar.YEAR)

        val gd = g.get(Calendar.DAY_OF_MONTH)
        val gm = g.get(Calendar.MONTH)
        val gy = g.get(Calendar.YEAR)
        return DayInfo(
            dayName = DAYS[g.get(Calendar.DAY_OF_WEEK) - 1],
            hijriDay = hd,
            hijriMonth = HIJRI_MONTHS[hm],
            hijriYear = hy,
            gregDay = gd,
            gregMonth = GREG_MONTHS[gm],
            gregYear = gy,
            key = "$gy-$gm-$gd/$adjust",
        )
    }

    /** يحوّل 25 إلى ٢٥ */
    fun digits(n: Int, arabic: Boolean): String {
        val s = n.toString()
        if (!arabic) return s
        return buildString { s.forEach { append(if (it in '0'..'9') '٠' + (it - '0') else it) } }
    }
}
