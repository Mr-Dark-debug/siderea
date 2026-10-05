package io.github.mrdarkdebug.siderea.export

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import io.github.mrdarkdebug.siderea.MainActivity
import io.github.mrdarkdebug.siderea.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps the process alive and shows progress while [ExportCoordinator] works. It does no encoding itself: it
 * watches the coordinator and stops as soon as nothing is running.
 */
@AndroidEntryPoint
class ExportService : Service() {
    @Inject lateinit var coordinator: ExportCoordinator

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        ensureChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification(coordinator.state.value as? ExportState.Working),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        acquireWakeLock()
        scope.launch {
            coordinator.state.collect { state ->
                if (state is ExportState.Working) {
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(state))
                } else {
                    shutDown()
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(
        startId: Int,
        fgsType: Int,
    ) {
        // Android limits how long a data-sync service may run in a day; stop the export rather than be killed.
        coordinator.cancel()
        shutDown()
    }

    override fun onDestroy() {
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    private fun shutDown() {
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notification(state: ExportState.Working?): Notification {
        val builder =
            NotificationCompat
                .Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(if (state != null) "Exporting ${state.title.lowercase()}" else "Exporting")
                .setContentText(state?.phase ?: "Getting ready")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentIntent(
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
        if (state != null && state.total > 0) builder.setProgress(state.total, state.done, false)
        return builder.build()
    }

    private fun ensureChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Exports", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Progress while a video or archive is being made."
                setShowBadge(false)
            },
        )
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "siderea:export").apply { acquire(MAX_WAKE_MS) }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    companion object {
        private const val CHANNEL = "export"
        private const val NOTIFICATION_ID = 4203
        private const val MAX_WAKE_MS = 2L * 60 * 60 * 1000

        fun start(context: Context) {
            context.startForegroundService(Intent(context, ExportService::class.java))
        }
    }
}
