package com.khalil.noor.lock

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.content.res.ResourcesCompat
import com.khalil.noor.R
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

data class LockTheme(
    val name: String,
    val top: Int, val mid: Int, val bottom: Int,
    val text: Int, val accent: Int,
    val stars: Boolean, val pattern: Boolean,
    val photo: Boolean = false,
)

object WallpaperRenderer {

    val THEMES = listOf(
        LockTheme("ليل", 0xFF0A0F24.toInt(), 0xFF18224A.toInt(), 0xFF3B2C5E.toInt(),
            0xFFF5EBD7.toInt(), 0xFFE8C77A.toInt(), stars = true, pattern = false),
        LockTheme("زمرد", 0xFF0B2E25.toInt(), 0xFF134A3B.toInt(), 0xFF1F6B52.toInt(),
            0xFFF6EBD0.toInt(), 0xFFE3BE6E.toInt(), stars = false, pattern = true),
        LockTheme("رمل", 0xFFF6E9D0.toInt(), 0xFFE8CFA5.toInt(), 0xFFCFA672.toInt(),
            0xFF3E2A18.toInt(), 0xFF8C5E26.toInt(), stars = false, pattern = true),
        LockTheme("ورد", 0xFFF8E1DE.toInt(), 0xFFE8B9BC.toInt(), 0xFFB9808C.toInt(),
            0xFF452330.toInt(), 0xFF8E4A5A.toInt(), stars = false, pattern = false),
        LockTheme("صورتي", Color.BLACK, Color.BLACK, Color.BLACK,
            0xFFFFFFFF.toInt(), 0xFFF0D08A.toInt(), stars = false, pattern = false, photo = true),
    )

    val FONTS = listOf(
        "نسخ" to R.font.amiri_bold,
        "رقعة" to R.font.aref_ruqaa_bold,
        "كوفي" to R.font.reem_kufi,
        "حديث" to R.font.tajawal_bold,
    )

    fun screenSize(c: Context): Pair<Int, Int> {
        val wm = c.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.maximumWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            dm.widthPixels to dm.heightPixels
        }
    }

    fun render(c: Context, s: LockStyle, day: DayInfo, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val t = THEMES.getOrElse(s.theme) { THEMES[0] }
        val face: Typeface = ResourcesCompat.getFont(c, FONTS.getOrElse(s.font) { FONTS[0] }.second)
            ?: Typeface.DEFAULT_BOLD

        // ---------- الخلفية ----------
        val photo = if (t.photo) loadPhoto(c, w, h) else null
        if (photo != null) {
            drawCenterCrop(cv, photo, w, h)
            val shade = Paint().apply {
                shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
                    intArrayOf(0x33000000, 0x22000000, 0xAA000000.toInt()),
                    floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
            }
            cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), shade)
        } else {
            val bgTop = if (t.photo) 0xFF1A1A2E.toInt() else t.top
            val bgMid = if (t.photo) 0xFF2A2340.toInt() else t.mid
            val bgBot = if (t.photo) 0xFF3B2C5E.toInt() else t.bottom
            val bg = Paint().apply {
                shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
                    intArrayOf(bgTop, bgMid, bgBot), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            }
            cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)
            // توهج خفيف خلف التاريخ
            val glow = Paint().apply {
                shader = RadialGradient(w / 2f, h * s.posY, w * 0.75f,
                    intArrayOf(withAlpha(t.accent, 0x2A), 0x00000000), null, Shader.TileMode.CLAMP)
            }
            cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), glow)
        }
        if (t.stars) drawStars(cv, w, h, day.key.hashCode(), t.accent)
        if (t.pattern) drawPattern(cv, w, h, t.text)

        // ---------- نصوص التاريخ ----------
        val cx = w / 2f
        val u = w / 100f
        val lines = mutableListOf<Line>()
        lines += Line(day.dayName, 6.6f * u, t.accent, 1.55f)
        lines += Line(HijriDate.digits(day.hijriDay, s.arabicDigits), 30f * u, t.text, 1.05f)
        lines += Line(day.hijriMonth, 10f * u, t.text, 1.45f)
        lines += Line(HijriDate.digits(day.hijriYear, s.arabicDigits) + " هـ", 5.6f * u, withAlpha(t.text, 0xD0), 1.6f)
        lines += Line("—ORN—", 4f * u, t.accent, 2.2f)
        if (s.showGreg) {
            val g = "${HijriDate.digits(day.gregDay, s.arabicDigits)} ${day.gregMonth} ${HijriDate.digits(day.gregYear, s.arabicDigits)} م"
            lines += Line(g, 5f * u, withAlpha(t.text, 0xE0), 1.6f)
        }
        if (s.extraText.isNotBlank()) {
            lines += Line(s.extraText.trim(), 4.6f * u, withAlpha(t.accent, 0xF0), 1.8f)
        }

        val total = lines.sumOf { (it.size * it.lead).toDouble() }.toFloat()
        var y = h * s.posY - total / 2f

        // بطاقة شفافة خلف النص لو كانت صورة
        if (photo != null) {
            val card = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40000000 }
            val r = RectF(w * 0.08f, y - 2f * u, w * 0.92f, y + total + 2f * u)
            cv.drawRoundRect(r, 6f * u, 6f * u, card)
        }

        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = face
            textAlign = Paint.Align.CENTER
        }
        for (ln in lines) {
            val slot = ln.size * ln.lead
            if (ln.text == "—ORN—") {
                drawOrnament(cv, cx, y + slot / 2f, w * 0.46f, ln.size, ln.color)
            } else {
                p.textSize = ln.size
                p.color = ln.color
                if (photo != null) p.setShadowLayer(1.2f * u, 0f, 0.3f * u, 0x88000000.toInt())
                else p.clearShadowLayer()
                fitWidth(p, ln.text, w * 0.86f)
                val fm = p.fontMetrics
                val base = y + slot / 2f - (fm.ascent + fm.descent) / 2f
                cv.drawText(ln.text, cx, base, p)
            }
            y += slot
        }
        return bmp
    }

    /** يصمم الخلفية ويركّبها على شاشة القفل */
    fun apply(c: Context): Boolean {
        val s = LockPrefs.load(c)
        val day = HijriDate.today(s.hijriAdjust)
        val (w, h) = screenSize(c)
        val bmp = render(c, s, day, w, h)
        val wm = WallpaperManager.getInstance(c)
        return try {
            val flags = if (s.alsoHome) WallpaperManager.FLAG_LOCK or WallpaperManager.FLAG_SYSTEM
            else WallpaperManager.FLAG_LOCK
            wm.setBitmap(bmp, null, true, flags)
            LockPrefs.setLastApplied(c, day.key)
            true
        } catch (e: Exception) {
            false
        } finally {
            bmp.recycle()
        }
    }

    // ---------------------------------------------------------------

    private data class Line(val text: String, val size: Float, val color: Int, val lead: Float)

    private fun fitWidth(p: Paint, text: String, maxW: Float) {
        val m = p.measureText(text)
        if (m > maxW) p.textSize = p.textSize * maxW / m
    }

    private fun withAlpha(color: Int, a: Int) = (color and 0x00FFFFFF) or (a shl 24)

    private fun drawOrnament(cv: Canvas, cx: Float, cy: Float, width: Float, size: Float, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; strokeWidth = size * 0.09f }
        val r = size * 0.42f
        val gap = r * 2.2f
        p.alpha = 0xB0
        cv.drawLine(cx - width / 2f, cy, cx - gap, cy, p)
        cv.drawLine(cx + gap, cy, cx + width / 2f, cy, p)
        p.alpha = 0xFF
        drawStar8(cv, cx, cy, r, p)
        // نقطتين صغار
        cv.drawCircle(cx - width / 2f - r * 0.5f, cy, r * 0.18f, p)
        cv.drawCircle(cx + width / 2f + r * 0.5f, cy, r * 0.18f, p)
    }

    /** نجمة ثمانية (ربع الحزب) */
    private fun drawStar8(cv: Canvas, cx: Float, cy: Float, r: Float, p: Paint) {
        val path = Path()
        for (k in 0 until 2) {
            val a0 = Math.PI / 4 * k
            for (i in 0 until 4) {
                val a = a0 + Math.PI / 2 * i
                val x = cx + (r * cos(a)).toFloat()
                val y = cy + (r * sin(a)).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
        }
        p.style = Paint.Style.FILL
        cv.drawPath(path, p)
    }

    private fun drawStars(cv: Canvas, w: Int, h: Int, seed: Int, accent: Int) {
        val rnd = Random(seed)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(140) {
            val x = rnd.nextFloat() * w
            val y = rnd.nextFloat() * h * 0.9f
            val r = (0.4f + rnd.nextFloat() * 1.6f) * w / 1080f * 2f
            p.color = if (rnd.nextInt(6) == 0) accent else Color.WHITE
            p.alpha = 40 + rnd.nextInt(150)
            cv.drawCircle(x, y, r, p)
        }
    }

    private fun drawPattern(cv: Canvas, w: Int, h: Int, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            alpha = 0x0E
            style = Paint.Style.STROKE
            strokeWidth = w / 540f
        }
        val step = w / 6f
        var row = 0
        var y = 0f
        while (y < h + step) {
            var x = if (row % 2 == 0) 0f else step / 2f
            while (x < w + step) {
                val path = Path()
                val r = step * 0.32f
                for (k in 0 until 2) {
                    val a0 = Math.PI / 4 * k
                    for (i in 0 until 4) {
                        val a = a0 + Math.PI / 2 * i
                        val px = x + (r * cos(a)).toFloat()
                        val py = y + (r * sin(a)).toFloat()
                        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    path.close()
                }
                cv.drawPath(path, p)
                x += step
            }
            y += step * 0.866f
            row++
        }
    }

    private fun loadPhoto(c: Context, w: Int, h: Int): Bitmap? {
        val f = LockPrefs.photoFile(c)
        if (!f.exists()) return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, o)
        var sample = 1
        while (o.outWidth / (sample * 2) >= w && o.outHeight / (sample * 2) >= h) sample *= 2
        return BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun drawCenterCrop(cv: Canvas, b: Bitmap, w: Int, h: Int) {
        val scale = max(w / b.width.toFloat(), h / b.height.toFloat())
        val sw = (w / scale).toInt()
        val sh = (h / scale).toInt()
        val sx = (b.width - sw) / 2
        val sy = (b.height - sh) / 2
        cv.drawBitmap(b, Rect(sx, sy, sx + sw, sy + sh), Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
    }
}
