package com.khalil.noor.dl

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.khalil.noor.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** يبقي التحميل شغّال لو طلعت من التطبيق، ويعرض التقدّم في الإشعارات */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watcher: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannels(this)
        ServiceCompat.startForeground(
            this, ID_PROGRESS, progressNotif("يبدأ التحميل…", 0, true),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        if (watcher == null) {
            watcher = scope.launch {
                Downloader.state.collectLatest { st ->
                    val nm = getSystemService(NotificationManager::class.java)
                    when (st) {
                        is DlState.Downloading ->
                            nm.notify(ID_PROGRESS, progressNotif(st.info.title, st.progress.toInt(), st.progress <= 0f))
                        is DlState.Done -> {
                            nm.notify(ID_DONE, doneNotif(st))
                            finish()
                        }
                        is DlState.Error -> {
                            nm.notify(ID_DONE, simpleNotif("ما اكتمل التحميل", st.message))
                            finish()
                        }
                        else -> finish()
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun finish() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun progressNotif(title: String, p: Int, indeterminate: Boolean): Notification =
        NotificationCompat.Builder(this, CH_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(if (indeterminate) "جاري التحميل…" else "$p٪")
            .setProgress(100, p, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp())
            .build()

    private fun doneNotif(st: DlState.Done): Notification {
        val open = st.uri?.let {
            PendingIntent.getActivity(
                this, 1,
                Intent(Intent.ACTION_VIEW).setDataAndType(it, if (st.audio) "audio/*" else "video/*")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE,
            )
        } ?: openApp()
        return NotificationCompat.Builder(this, CH_DONE)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("تم التحميل ✓")
            .setContentText(st.info.title)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
    }

    private fun simpleNotif(title: String, text: String): Notification =
        NotificationCompat.Builder(this, CH_DONE)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()

    companion object {
        private const val CH_PROGRESS = "progress"
        private const val CH_DONE = "done"
        private const val ID_PROGRESS = 10
        private const val ID_DONE = 11

        fun ensureChannels(c: Context) {
            val nm = c.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CH_PROGRESS, "تقدّم التحميل", NotificationManager.IMPORTANCE_LOW)
            )
            nm.createNotificationChannel(
                NotificationChannel(CH_DONE, "انتهاء التحميل", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }
}
