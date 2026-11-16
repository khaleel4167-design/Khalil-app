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
        if (s.layout == 1) drawCompact(cv, c, s, t, day, face, w, h, photo != null)
        else drawFull(cv, s, t, day, face, w, h, photo != null)
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

    /**
     * بطاقة التاريخ اللي تطلع في الإشعارات على شاشة القفل،
     * بنفس ألوان وخط الخلفية المختارة. [compact] = سطر واحد للإشعار المصغّر
     */
    fun renderCard(c: Context, s: LockStyle, day: DayInfo, compact: Boolean): Bitmap {
        val w = 1080
        val h = if (compact) 170 else 340
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val base = THEMES.getOrElse(s.theme) { THEMES[0] }
        val t = if (base.photo) THEMES[0] else base
        val face: Typeface = ResourcesCompat.getFont(c, FONTS.getOrElse(s.font) { FONTS[0] }.second)
            ?: Typeface.DEFAULT_BOLD
        val r = h * if (compact) 0.3f else 0.16f
        val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())

        // خلفية البطاقة بتدرّج ألوان الثيم
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(w.toFloat(), 0f, 0f, h.toFloat(),
                intArrayOf(t.top, t.mid, t.bottom), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        }
        cv.drawRoundRect(rect, r, r, bg)
        cv.save()
        val clip = Path().apply { addRoundRect(rect, r, r, Path.Direction.CW) }
        cv.clipPath(clip)
        if (t.stars) drawStars(cv, w, h * 3, day.key.hashCode(), t.accent)
        if (t.pattern) drawPattern(cv, w, h, t.text)
        cv.restore()

        val d = { n: Int -> HijriDate.digits(n, s.arabicDigits) }
        val pad = h * if (compact) 0.22f else 0.14f
        val right = w - pad

        if (compact) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face; textAlign = Paint.Align.RIGHT; textSize = h * 0.42f; color = t.text }
            val line = "${day.dayName} ${d(day.hijriDay)} ${day.hijriMonth} ${d(day.hijriYear)} هـ"
            fitWidth(p, line, w - pad * 2 - h * 0.5f)
            val fm = p.fontMetrics
            cv.drawText(line, right, h / 2f - (fm.ascent + fm.descent) / 2f, p)
            val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = t.accent }
            drawStar8(cv, pad + h * 0.18f, h / 2f, h * 0.16f, sp)
            return bmp
        }

        // الرقم الكبير على اليمين
        val num = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face; textAlign = Paint.Align.CENTER; textSize = h * 0.62f; color = t.accent }
        val numText = d(day.hijriDay)
        val numW = maxOf(num.measureText(numText), h * 0.45f)
        val numCx = right - numW / 2f
        val nfm = num.fontMetrics
        cv.drawText(numText, numCx, h / 2f - (nfm.ascent + nfm.descent) / 2f, num)

        // خط فاصل
        val sep = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = t.accent; alpha = 0x80; strokeWidth = 3f }
        val sx = right - numW - pad * 0.7f
        cv.drawLine(sx, h * 0.2f, sx, h * 0.8f, sep)

        val textRight = sx - pad * 0.7f
        val maxW = textRight - pad - h * 0.3f
        val p1 = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face; textAlign = Paint.Align.RIGHT; textSize = h * 0.2f; color = t.text }
        val p2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face; textAlign = Paint.Align.RIGHT; textSize = h * 0.13f; color = withAlpha(t.text, 0xC8) }
        val l1 = "${day.dayName}، ${day.hijriMonth}"
        val l2 = if (s.showGreg) "${d(day.hijriYear)} هـ  •  ${d(day.gregDay)} ${day.gregMonth} ${d(day.gregYear)} م" else "${d(day.hijriYear)} هـ"
        val l3 = s.extraText.trim()
        fitWidth(p1, l1, maxW); fitWidth(p2, l2, maxW)
        val p3 = Paint(p2).apply { color = t.accent }
        if (l3.isNotEmpty()) fitWidth(p3, l3, maxW)
        val lines = mutableListOf(p1 to l1, p2 to l2)
        if (l3.isNotEmpty()) lines += p3 to l3
        val slots = lines.map { it.first.textSize * 1.45f }
        var y = h / 2f - slots.sum() / 2f
        lines.forEachIndexed { i, (p, text) ->
            val fm = p.fontMetrics
            cv.drawText(text, textRight, y + slots[i] / 2f - (fm.ascent + fm.descent) / 2f, p)
            y += slots[i]
        }
        // نجمة صغيرة على اليسار
        val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = t.accent; alpha = 0xD0 }
        drawStar8(cv, pad + h * 0.08f, h / 2f, h * 0.07f, sp)
        return bmp
    }

    /** أيقونة شريط الحالة: رقم اليوم الهجري */
    fun renderDayIcon(c: Context, day: DayInfo, arabic: Boolean): Bitmap {
        val size = 96
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = ResourcesCompat.getFont(c, R.font.tajawal_bold) ?: Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            color = Color.WHITE
            textSize = size * 0.78f
        }
        val text = HijriDate.digits(day.hijriDay, arabic)
        fitWidth(p, text, size * 0.95f)
        val fm = p.fontMetrics
        cv.drawText(text, size / 2f, size / 2f - (fm.ascent + fm.descent) / 2f, p)
        return bmp
    }

    private fun drawFull(cv: Canvas, s: LockStyle, t: LockTheme, day: DayInfo, face: Typeface,
                         w: Int, h: Int, hasPhoto: Boolean) {
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
        if (hasPhoto) {
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
                if (hasPhoto) p.setShadowLayer(1.2f * u, 0f, 0.3f * u, 0x88000000.toInt())
                else p.clearShadowLayer()
                fitWidth(p, ln.text, w * 0.86f)
                val fm = p.fontMetrics
                val base = y + slot / 2f - (fm.ascent + fm.descent) / 2f
                cv.drawText(ln.text, cx, base, p)
            }
            y += slot
        }
    }

    /** الشكل البسيط: بطاقة صغيرة شفافة تحت الساعة، والخلفية تبان كلها */
    private fun drawCompact(cv: Canvas, c: Context, s: LockStyle, t: LockTheme, day: DayInfo, face: Typeface,
                            w: Int, h: Int, hasPhoto: Boolean) {
        val u = w / 100f
        val d = { n: Int -> HijriDate.digits(n, s.arabicDigits) }
        val line1 = "${day.dayName} ${d(day.hijriDay)} ${day.hijriMonth}"
        val line2 = if (s.showGreg) "${d(day.hijriYear)} هـ  •  ${d(day.gregDay)} ${day.gregMonth} ${d(day.gregYear)} م"
        else "${d(day.hijriYear)} هـ"
        val extra = s.extraText.trim()

        val p1 = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face; textAlign = Paint.Align.CENTER; textSize = 6.2f * u }
        val p2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face; textAlign = Paint.Align.CENTER; textSize = 4.1f * u }
        val p3 = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face; textAlign = Paint.Align.CENTER; textSize = 3.7f * u }
        fitWidth(p1, line1, w * 0.78f); fitWidth(p2, line2, w * 0.78f); if (extra.isNotEmpty()) fitWidth(p3, extra, w * 0.78f)

        val h1 = p1.textSize * 1.5f
        val h2 = p2.textSize * 1.55f
        val h3 = if (extra.isNotEmpty()) p3.textSize * 1.6f else 0f
        val padV = 2.6f * u
        val total = h1 + h2 + h3 + padV * 2
        val textW = maxOf(p1.measureText(line1), p2.measureText(line2), if (extra.isNotEmpty()) p3.measureText(extra) else 0f)
        val cardW = minOf(w * 0.9f, textW + 14f * u)
        val cx = w / 2f
        val top = h * s.posY - total / 2f

        // بطاقة زجاجية
        val light = !hasPhoto && luminance(t.mid) > 0.6f
        val card = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (light) 0x55FFFFFF else 0x3A000000 }
        val rect = RectF(cx - cardW / 2f, top, cx + cardW / 2f, top + total)
        cv.drawRoundRect(rect, 5f * u, 5f * u, card)
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 0.25f * u; color = withAlpha(t.accent, 0x70)
        }
        cv.drawRoundRect(rect, 5f * u, 5f * u, edge)

        // نجمتين صغار على الأطراف
        val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = t.accent }
        drawStar8(cv, rect.left + 4f * u, top + padV + h1 / 2f, 1.3f * u, sp)
        drawStar8(cv, rect.right - 4f * u, top + padV + h1 / 2f, 1.3f * u, sp)

        val textColor = t.text
        var y = top + padV
        fun put(p: Paint, text: String, slot: Float, color: Int) {
            p.color = color
            if (hasPhoto) p.setShadowLayer(0.8f * u, 0f, 0.2f * u, 0x88000000.toInt())
            val fm = p.fontMetrics
            cv.drawText(text, cx, y + slot / 2f - (fm.ascent + fm.descent) / 2f, p)
            y += slot
        }
        put(p1, line1, h1, textColor)
        put(p2, line2, h2, withAlpha(textColor, 0xD8))
        if (extra.isNotEmpty()) put(p3, extra, h3, t.accent)
    }

    private fun luminance(c: Int): Float {
        val r = (c shr 16 and 0xFF) / 255f; val g = (c shr 8 and 0xFF) / 255f; val b = (c and 0xFF) / 255f
        return 0.299f * r + 0.587f * g + 0.114f * b
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
