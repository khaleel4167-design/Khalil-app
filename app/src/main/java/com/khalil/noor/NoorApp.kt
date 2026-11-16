package com.khalil.noor

import android.app.Application
import com.khalil.noor.dl.DownloadService
import com.khalil.noor.dl.Downloader
import com.khalil.noor.lock.DailyScheduler

class NoorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DownloadService.ensureChannels(this)
        Downloader.init(this)
        DailyScheduler.sync(this)
    }
}
