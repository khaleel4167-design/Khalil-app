package com.khalil.noor.lock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.khalil.noor.MainActivity
import com.khalil.noor.R

/**
 * بطاقة التاريخ على شاشة القفل بدون ما نغيّر الخلفية:
 * إشعار ثابت بتصميم الثيم، ورقم اليوم الهجري كأيقونة في شريط الحالة.
 */
object DateNotifier {
    private const val CHANNEL = "hijri_date"
    private const val ID = 21

    private fun ensureChannel(c: Context) {
        val ch = NotificationChannel(CHANNEL, "التاريخ الهجري", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "بطاقة التاريخ على شاشة القفل"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        c.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    /** يرسل أو يحدّث البطاقة. يرجع false لو الإشعارات مقفلة للتطبيق */
    fun post(c: Context): Boolean {
        val nm = NotificationManagerCompat.from(c)
        if (!nm.areNotificationsEnabled()) return false
        ensureChannel(c)
        val s = LockPrefs.load(c)
        val day = HijriDate.today(s.hijriAdjust)
        val d = { n: Int -> HijriDate.digits(n, s.arabicDigits) }

        val small = RemoteViews(c.packageName, R.layout.notif_card_small).apply {
            setImageViewBitmap(R.id.card, WallpaperRenderer.renderCard(c, s, day, compact = true))
        }
        val big = RemoteViews(c.packageName, R.layout.notif_card_big).apply {
            setImageViewBitmap(R.id.card, WallpaperRenderer.renderCard(c, s, day, compact = false))
        }
        val open = PendingIntent.getActivity(
            c, 3, Intent(c, MainActivity::class.java).putExtra("tab", 1),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = "${day.dayName} ${d(day.hijriDay)} ${day.hijriMonth} ${d(day.hijriYear)} هـ"
        val text = "${d(day.gregDay)} ${day.gregMonth} ${d(day.gregYear)} م"

        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(IconCompat.createWithBitmap(WallpaperRenderer.renderDayIcon(c, day, s.arabicDigits)))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(small)
            .setCustomBigContentView(big)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(open)
            .build()
        return try {
            nm.notify(ID, n)
            LockPrefs.setLastApplied(c, day.key + "/n")
            true
        } catch (e: SecurityException) {
            false
        }
    }

    fun cancel(c: Context) {
        NotificationManagerCompat.from(c).cancel(ID)
    }
}
