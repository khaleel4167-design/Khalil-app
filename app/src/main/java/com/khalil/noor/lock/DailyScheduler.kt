package com.khalil.noor.lock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * يحدّث الخلفية كل يوم:
 * - منبّه دقيق بعد منتصف الليل بدقيقة
 * - ومهمة احتياطية كل 3 ساعات تتأكد إن خلفية اليوم مركّبة (لو النظام أخّر المنبّه)
 */
object DailyScheduler {
    private const val WORK = "noor-daily-check"

    fun sync(c: Context) {
        val s = LockPrefs.load(c)
        // ما نغيّر الخلفية تلقائياً إلا بعد ما تطبّقها هي أول مرة بنفسها
        val enabled = s.autoDaily && LockPrefs.lastApplied(c) != null
        if (enabled) {
            scheduleAlarm(c)
            WorkManager.getInstance(c).enqueueUniquePeriodicWork(
                WORK, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<CheckWorker>(3, TimeUnit.HOURS).build(),
            )
        } else {
            cancelAlarm(c)
            WorkManager.getInstance(c).cancelUniqueWork(WORK)
        }
    }

    /** يطبّق حسب الطريقة المختارة: خلفية أو بطاقة إشعار */
    fun applyNow(c: Context): Boolean {
        val s = LockPrefs.load(c)
        return if (s.mode == 1) {
            DateNotifier.post(c)
        } else {
            DateNotifier.cancel(c)
            WallpaperRenderer.apply(c)
        }
    }

    /** يحدّث تاريخ اليوم لو ما تحدّث */
    fun ensureToday(c: Context) {
        val s = LockPrefs.load(c)
        if (!s.autoDaily || LockPrefs.lastApplied(c) == null) return
        if (s.mode == 1) {
            // البطاقة نعيد إرسالها دايماً: تنمسح بعد إعادة التشغيل أو لو انسحبت
            DateNotifier.post(c)
            return
        }
        val key = HijriDate.today(s.hijriAdjust).key
        if (LockPrefs.lastApplied(c) != key) WallpaperRenderer.apply(c)
    }

    private fun pending(c: Context) = PendingIntent.getBroadcast(
        c, 1, Intent(c, DailyReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun scheduleAlarm(c: Context) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val next = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 1)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        try {
            if (canExact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending(c))
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending(c))
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending(c))
        }
    }

    private fun cancelAlarm(c: Context) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(c))
    }
}

class CheckWorker(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result {
        DailyScheduler.ensureToday(applicationContext)
        return Result.success()
    }
}

/** يشتغل بعد منتصف الليل */
class DailyReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val pr = goAsync()
        Thread {
            try {
                DailyScheduler.ensureToday(c)
                DailyScheduler.sync(c) // يجدول الليلة الجاية
            } finally {
                pr.finish()
            }
        }.start()
    }
}

/** بعد تشغيل الجوال أو تغيير الوقت أو تحديث التطبيق */
class SystemReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val pr = goAsync()
        Thread {
            try {
                DailyScheduler.sync(c)
                DailyScheduler.ensureToday(c)
            } finally {
                pr.finish()
            }
        }.start()
    }
}
