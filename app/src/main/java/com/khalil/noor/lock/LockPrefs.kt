package com.khalil.noor.lock

import android.content.Context
import java.io.File

/** إعدادات شكل شاشة القفل */
data class LockStyle(
    val theme: Int = 0,
    /** 0 = كامل في وسط الشاشة، 1 = بسيط: سطر صغير فوق */
    val layout: Int = 0,
    val font: Int = 0,
    val posY: Float = 0.58f,
    val arabicDigits: Boolean = true,
    val showGreg: Boolean = true,
    val extraText: String = "",
    val hijriAdjust: Int = 0,
    val autoDaily: Boolean = true,
    val alsoHome: Boolean = false,
)

object LockPrefs {
    private const val NAME = "lock"

    fun load(c: Context): LockStyle {
        val p = c.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return LockStyle(
            theme = p.getInt("theme", 0),
            layout = p.getInt("layout", 0),
            font = p.getInt("font", 0),
            posY = p.getFloat("posY", 0.58f),
            arabicDigits = p.getBoolean("arabicDigits", true),
            showGreg = p.getBoolean("showGreg", true),
            extraText = p.getString("extraText", "") ?: "",
            hijriAdjust = p.getInt("hijriAdjust", 0),
            autoDaily = p.getBoolean("autoDaily", true),
            alsoHome = p.getBoolean("alsoHome", false),
        )
    }

    fun save(c: Context, s: LockStyle) {
        c.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putInt("theme", s.theme)
            .putInt("layout", s.layout)
            .putInt("font", s.font)
            .putFloat("posY", s.posY)
            .putBoolean("arabicDigits", s.arabicDigits)
            .putBoolean("showGreg", s.showGreg)
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

    /** الصورة اللي اختارتها من الاستوديو تنحفظ نسخة منها داخل التطبيق */
    fun photoFile(c: Context): File = File(c.filesDir, "bg_photo.jpg")
}
