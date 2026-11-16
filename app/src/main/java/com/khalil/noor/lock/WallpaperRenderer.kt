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
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.content.res.ResourcesCompat
import com.khalil.noor.R
import java.io.FileInputStream
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

data class LockTheme(
    val name: String,
    val top: Int, val mid: Int, val bottom: Int,
    val text: Int, val accent: Int,
    val stars: Boolean, val pattern: Boolean,
    /** زجاجي: بطاقة شفافة تبين الصورة من وراها */
    val glass: Boolean = false,
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
        LockTheme("زجاجي", 0xFF0A0F24.toInt(), 0xFF18224A.toInt(), 0xFF3B2C5E.toInt(),
            0xFFFFFFFF.toInt(), 0xFFF0D08A.toInt(), stars = true, pattern = false, glass = true),
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

    private fun theme(s: LockStyle) = THEMES.getOrElse(s.theme) { THEMES.last() }
    private fun face(c: Context, s: LockStyle): Typeface =
        ResourcesCompat.getFont(c, FONTS.getOrElse(s.font) { FONTS[0] }.second) ?: Typeface.DEFAULT_BOLD

    /** الخلفية الكاملة: صورتها (أو لون) + بطاقة التاريخ في المكان المختار */
    fun render(c: Context, s: LockStyle, day: DayInfo, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val t = theme(s)

        val photo = if (s.usePhoto) loadPhoto(c, w, h) else null
        if (photo != null) {
            drawCenterCrop(cv, photo, w, h)
            photo.recycle()
        } else {
            val bg = Paint().apply {
                shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
                    intArrayOf(t.top, t.mid, t.bottom), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            }
            cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)
            if (t.stars) drawStars(cv, w, h, day.key.hashCode(), t.accent, 140)
            if (t.pattern) drawPattern(cv, w, h, t.text)
        }

        // البطاقة
        val cw = (w * 0.88f).toInt()
        val card = renderCard(c, s, day, cw)
        val left = (w - cw) / 2f
        val top = (h * s.posY - card.height / 2f).coerceIn(h * 0.04f, h * 0.96f - card.height)
        val r = cw * 0.06f
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x01000000
            setShadowLayer(cw * 0.03f, 0f, cw * 0.012f, 0x55000000)
        }
        if (!t.glass) cv.drawRoundRect(RectF(left, top, left + cw, top + card.height), r, r, shadow)
        cv.drawBitmap(card, left, top, Paint(Paint.FILTER_BITMAP_FLAG))
        card.recycle()
        return bmp
    }

    /** بطاقة التاريخ بعرض [w] */
    fun renderCard(c: Context, s: LockStyle, day: DayInfo, w: Int): Bitmap {
        val t = theme(s)
        val face = face(c, s)
        val big = s.cardSize == 1
        val extra = s.extraText.trim()
        val d = { n: Int -> HijriDate.digits(n, s.arabicDigits) }

        val h = when {
            big && s.dates == 0 -> w * (if (extra.isEmpty()) 0.46f else 0.54f)
            big -> w * 0.34f
            s.dates == 0 -> w * (if (extra.isEmpty()) 0.2f else 0.27f)
            else -> w * (if (extra.isEmpty()) 0.14f else 0.21f)
        }.toInt()

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val r = w * 0.06f
        val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())

        // خلفية البطاقة
        if (t.glass) {
            cv.drawRoundRect(rect, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x52000000 })
            val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = w * 0.003f; color = 0x55FFFFFF
            }
            val in2 = edge.strokeWidth / 2
            cv.drawRoundRect(RectF(in2, in2, w - in2, h - in2), r, r, edge)
        } else {
            cv.drawRoundRect(rect, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(w.toFloat(), 0f, 0f, h.toFloat(),
                    intArrayOf(t.top, t.mid, t.bottom), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
            })
            cv.save()
            cv.clipPath(Path().apply { addRoundRect(rect, r, r, Path.Direction.CW) })
            if (t.stars) drawStars(cv, w, h, day.key.hashCode(), t.accent, 50)
            if (t.pattern) drawPattern(cv, w, h, t.text)
            cv.restore()
        }

        val shadowText = t.glass
        fun paint(size: Float, color: Int, align: Paint.Align = Paint.Align.CENTER) =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = face; textSize = size; this.color = color; textAlign = align
                if (shadowText) setShadowLayer(size * 0.06f, 0f, size * 0.02f, 0x66000000)
            }
        fun drawIn(p: Paint, text: String, x: Float, slotTop: Float, slotH: Float, maxW: Float) {
            fitWidth(p, text, maxW)
            val fm = p.fontMetrics
            cv.drawText(text, x, slotTop + slotH / 2f - (fm.ascent + fm.descent) / 2f, p)
        }

        val hijri = "${d(day.hijriDay)} ${day.hijriMonth} ${d(day.hijriYear)} هـ"
        val greg = "${d(day.gregDay)} ${day.gregMonth} ${d(day.gregYear)} م"
        val star = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = t.accent }

        if (!big) {
            // صغيرة: سطر أو سطرين في الوسط ونجمتين على الأطراف
            val lines = mutableListOf<Pair<Paint, String>>()
            when (s.dates) {
                0 -> {
                    lines += paint(w * 0.06f, t.text) to "${day.dayName} $hijri"
                    lines += paint(w * 0.045f, withAlpha(t.text, 0xD0)) to greg
                }
                1 -> lines += paint(w * 0.06f, t.text) to "${day.dayName} $hijri"
                else -> lines += paint(w * 0.06f, t.text) to "${day.dayName} $greg"
            }
            if (extra.isNotEmpty()) lines += paint(w * 0.042f, t.accent) to extra
            val slots = lines.map { it.first.textSize * 1.5f }
            var y = h / 2f - slots.sum() / 2f
            lines.forEachIndexed { i, (p, text) -> drawIn(p, text, w / 2f, y, slots[i], w * 0.8f); y += slots[i] }
            drawStar8(cv, w * 0.05f, h / 2f, w * 0.014f, star)
            drawStar8(cv, w * 0.95f, h / 2f, w * 0.014f, star)
            return bmp
        }

        if (s.dates == 0) {
            // كبيرة بالتقويمين: عمود هجري يمين وعمود ميلادي يسار
            val topSlot = h * 0.17f
            drawIn(paint(w * 0.05f, t.accent), day.dayName, w / 2f, h * 0.03f, topSlot, w * 0.6f)
            val colTop = h * 0.03f + topSlot
            val colH = if (extra.isEmpty()) h - colTop - h * 0.05f else h - colTop - h * 0.17f
            fun column(cx: Float, num: Int, month: String, year: String) {
                val pn = paint(colH * 0.5f, t.text)
                drawIn(pn, d(num), cx, colTop, colH * 0.52f, w * 0.4f)
                drawIn(paint(colH * 0.15f, t.text), month, cx, colTop + colH * 0.52f, colH * 0.24f, w * 0.4f)
                drawIn(paint(colH * 0.11f, withAlpha(t.text, 0xC0)), year, cx, colTop + colH * 0.76f, colH * 0.22f, w * 0.4f)
            }
            column(w * 0.74f, day.hijriDay, day.hijriMonth, "${d(day.hijriYear)} هـ")
            column(w * 0.26f, day.gregDay, day.gregMonth, "${d(day.gregYear)} م")
            val sep = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = t.accent; alpha = 0x90; strokeWidth = w * 0.003f }
            val mid = colTop + colH / 2f
            cv.drawLine(w / 2f, colTop + colH * 0.1f, w / 2f, mid - w * 0.03f, sep)
            cv.drawLine(w / 2f, mid + w * 0.03f, w / 2f, colTop + colH * 0.9f, sep)
            drawStar8(cv, w / 2f, mid, w * 0.016f, star)
            if (extra.isNotEmpty()) drawIn(paint(w * 0.045f, t.accent), extra, w / 2f, colTop + colH, h * 0.14f, w * 0.85f)
            return bmp
        }

        // كبيرة بتقويم واحد: الرقم يمين والتفاصيل جنبه
        val isH = s.dates == 1
        val pad = h * 0.14f
        val right = w - pad
        val num = paint(h * 0.62f, t.accent)
        val numText = d(if (isH) day.hijriDay else day.gregDay)
        val numW = max(num.measureText(numText), h * 0.45f)
        drawIn(num, numText, right - numW / 2f, 0f, h.toFloat(), numW * 1.2f)
        val sx = right - numW - pad * 0.7f
        cv.drawLine(sx, h * 0.2f, sx, h * 0.8f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = t.accent; alpha = 0x80; strokeWidth = w * 0.003f })
        val textRight = sx - pad * 0.7f
        val maxW = textRight - pad - h * 0.2f
        val lines = mutableListOf(
            paint(h * 0.2f, t.text, Paint.Align.RIGHT) to "${day.dayName}، ${if (isH) day.hijriMonth else day.gregMonth}",
            paint(h * 0.13f, withAlpha(t.text, 0xC8), Paint.Align.RIGHT) to
                (if (isH) "${d(day.hijriYear)} هـ" else "${d(day.gregYear)} م"),
        )
        if (extra.isNotEmpty()) lines += paint(h * 0.12f, t.accent, Paint.Align.RIGHT) to extra
        val slots = lines.map { it.first.textSize * 1.45f }
        var y = h / 2f - slots.sum() / 2f
        lines.forEachIndexed { i, (p, text) -> drawIn(p, text, textRight, y, slots[i], maxW); y += slots[i] }
        drawStar8(cv, pad + h * 0.08f, h / 2f, h * 0.07f, star)
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
     * ينسخ خلفية الجوال الحالية عشان نحط البطاقة فوقها.
     * أندرويد يطلب صلاحية "الوصول لكل الملفات" عشان يعطي الخلفية لتطبيق.
     */
    @android.annotation.SuppressLint("MissingPermission")
    fun importCurrentWallpaper(c: Context): Boolean {
        val wm = WallpaperManager.getInstance(c)
        val out = LockPrefs.photoFile(c)
        for (flag in intArrayOf(WallpaperManager.FLAG_LOCK, WallpaperManager.FLAG_SYSTEM)) {
            try {
                wm.getWallpaperFile(flag)?.use { pfd ->
                    FileInputStream(pfd.fileDescriptor).use { input -> out.outputStream().use { input.copyTo(it) } }
                    if (out.length() > 0) return true
                }
            } catch (e: Throwable) { /* نجرب اللي بعده */ }
        }
        return try {
            val dr = wm.drawable
            val b = (dr as? BitmapDrawable)?.bitmap ?: return false
            out.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            true
        } catch (e: Throwable) {
            false
        }
    }

    // ---------------------------------------------------------------

    private fun fitWidth(p: Paint, text: String, maxW: Float) {
        val m = p.measureText(text)
        if (m > maxW) p.textSize = p.textSize * maxW / m
    }

    private fun withAlpha(color: Int, a: Int) = (color and 0x00FFFFFF) or (a shl 24)

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

    private fun drawStars(cv: Canvas, w: Int, h: Int, seed: Int, accent: Int, count: Int) {
        val rnd = Random(seed)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(count) {
            val x = rnd.nextFloat() * w
            val y = rnd.nextFloat() * h
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
        if (!f.exists() || f.length() == 0L) return null
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
