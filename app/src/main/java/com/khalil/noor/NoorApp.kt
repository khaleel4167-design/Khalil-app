package com.khalil.noor

import android.app.Application
import com.khalil.noor.dl.DownloadService
import com.khalil.noor.dl.Downloader
import com.khalil.noor.lock.DailyScheduler

class NoorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DownloadService.ensureChannels(this)
        // تنظيف بطاقة الإشعار من النسخة السابقة
        getSystemService(android.app.NotificationManager::class.java).apply {
            cancel(21)
            deleteNotificationChannel("hijri_date")
        }
        Downloader.init(this)
        DailyScheduler.sync(this)
    }
}
