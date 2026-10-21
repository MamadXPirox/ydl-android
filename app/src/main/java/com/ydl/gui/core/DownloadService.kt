package com.ydl.gui.core

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
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.ydl.gui.MainActivity
import com.ydl.gui.R
import com.ydl.gui.YdlApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Keeps the process alive (foreground service) while downloads are running. */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        ServiceCompat.startForeground(
            this, NOTIF_ID, build("YDL GUI", 0, true),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        job?.cancel()
        job = scope.launch {
            (application as YdlApp).downloads.tasks.collect { list ->
                val active = list.filter { it.isActive }
                if (active.isEmpty()) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    val cur = active.firstOrNull { it.status == TaskStatus.DOWNLOADING } ?: active.first()
                    val title = if (active.size > 1) "${active.size} downloads" else cur.title
                    val text = buildString {
                        append(cur.title)
                        if (cur.speed.isNotEmpty()) append(" · ").append(cur.speed)
                        if (cur.eta.isNotEmpty()) append(" · ETA ").append(cur.eta)
                    }
                    val n = build(title, cur.percent.toInt(), cur.status != TaskStatus.DOWNLOADING, text)
                    runCatching { NotificationManagerCompat.from(this@DownloadService).notify(NOTIF_ID, n) }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun build(title: String, percent: Int, indeterminate: Boolean, text: String = ""): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(100, percent, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .build()
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.tab_downloads), NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "downloads"
        private const val NOTIF_ID = 1001
        fun start(ctx: Context) {
            runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, DownloadService::class.java)) }
        }
    }
}
