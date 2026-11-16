package com.khalil.noor.lock

import android.content.Context
import java.io.File

/** إعدادات شاشة القفل: خلفية (صورتها أو لون) + بطاقة التاريخ فوقها */
data class LockStyle(
    /** لون البطاقة: فهرس في WallpaperRenderer.THEMES (آخر واحد = زجاجي) */
    val theme: Int = 4,
    /** true = صورتها خلفية، false = خلفية ملوّنة بلون البطاقة */
    val usePhoto: Boolean = true,
    /** 0 = بطاقة صغيرة، 1 = بطاقة كبيرة */
    val cardSize: Int = 1,
    /** 0 = هجري وميلادي، 1 = هجري فقط، 2 = ميلادي فقط */
    val dates: Int = 0,
    val font: Int = 0,
    val posY: Float = 0.27f,
    val arabicDigits: Boolean = true,
    val extraText: String = "",
    val hijriAdjust: Int = 0,
    val autoDaily: Boolean = true,
    val alsoHome: Boolean = false,
)

object LockPrefs {
    private const val NAME = "lock2"

    fun load(c: Context): LockStyle {
        val p = c.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return LockStyle(
            theme = p.getInt("theme", 4),
            usePhoto = p.getBoolean("usePhoto", true),
            cardSize = p.getInt("cardSize", 1),
            dates = p.getInt("dates", 0),
            font = p.getInt("font", 0),
            posY = p.getFloat("posY", 0.27f),
            arabicDigits = p.getBoolean("arabicDigits", true),
            extraText = p.getString("extraText", "") ?: "",
            hijriAdjust = p.getInt("hijriAdjust", 0),
            autoDaily = p.getBoolean("autoDaily", true),
            alsoHome = p.getBoolean("alsoHome", false),
        )
    }

    fun save(c: Context, s: LockStyle) {
        c.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putInt("theme", s.theme)
            .putBoolean("usePhoto", s.usePhoto)
            .putInt("cardSize", s.cardSize)
            .putInt("dates", s.dates)
            .putInt("font", s.font)
            .putFloat("posY", s.posY)
            .putBoolean("arabicDigits", s.arabicDigits)
            .putString("extraText", s.extraText)
            .putInt("hijriAdjust", s.hijriAdjust)
            .putBoolean("autoDaily", s.autoDaily)
            .putBoolean("alsoHome", s.alsoHome)
            .apply()
    }

    fun lastApplied(c: Context): String? =
        c.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("lastApplied", null)

    fun setLastApplied(c: Context, key: String) {
        c.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().putString("lastApplied", key).apply()
    }

    /** نسخة من خلفيتها الأصلية (من الاستوديو أو من خلفية الجوال الحالية) */
    fun photoFile(c: Context): File = File(c.filesDir, "bg_photo.jpg")
}
