package ir.miaad.optionheatmap.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import ir.miaad.optionheatmap.core.monitor.MonitorDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DataSyncService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var pollingJob: Job? = null

    companion object {
        const val CHANNEL_ID = "data_sync_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "ir.miaad.optionheatmap.STOP_SERVICE"
        private const val POLL_INTERVAL_MS = 10_000L
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            pollingJob?.cancel()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        val notification = buildNotification("در حال اتصال به TSETMC...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        startPolling()
        return START_STICKY
    }

    private fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = serviceScope.launch {
            var counter = 0
            while (isActive) {
                val startedAt = SystemClock.elapsedRealtime()
                try {
                    MonitorDataStore.refreshAll()
                    counter++
                    updateNotification("دریافت داده واقعی • دور $counter")
                } catch (error: Exception) {
                    updateNotification("خطای موقت دریافت داده؛ تلاش مجدد...")
                }
                val elapsed = SystemClock.elapsedRealtime() - startedAt
                delay((POLL_INTERVAL_MS - elapsed).coerceAtLeast(0L))
            }
        }
    }

    private fun buildNotification(content: String): Notification {
        val stopIntent = Intent(this, DataSyncService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = packageManager.getLaunchIntentForPackage(packageName)
        val openPendingIntent = PendingIntent.getActivity(
            this, 1, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("زنجیره آپشن Heatmap")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPendingIntent)
            .addAction(0, "توقف", stopPendingIntent)
            .build()
    }

    private fun updateNotification(content: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(content))
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "رصد بازار", NotificationManager.IMPORTANCE_LOW).apply {
            description = "اعلان دائمی سرویس رصد"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OptionHeatmap::DataSyncWakeLock")
            .apply { acquire(6 * 60 * 60 * 1000L) }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    // Android 15+ برای سرویس dataSync پس از سهمیه زمانی این callback را فراخوانی می‌کند.
    override fun onTimeout(startId: Int, fgsType: Int) {
        pollingJob?.cancel()
        serviceScope.cancel()
        releaseWakeLock()
        stopSelf(startId)
    }

    override fun onDestroy() {
        pollingJob?.cancel()
        serviceScope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
