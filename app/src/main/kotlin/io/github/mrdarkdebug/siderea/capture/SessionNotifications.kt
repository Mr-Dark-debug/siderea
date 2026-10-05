package io.github.mrdarkdebug.siderea.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import io.github.mrdarkdebug.siderea.MainActivity
import io.github.mrdarkdebug.siderea.R
import io.github.mrdarkdebug.siderea.core.capture.session.SessionStatus
import io.github.mrdarkdebug.siderea.core.capture.timelapse.IntervalMath

/** The persistent progress notification and the "finished" one. */
class SessionNotifications(
    private val context: Context,
) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Capture sessions", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Progress of a timelapse or astro session, with a Stop button."
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(DONE_CHANNEL, "Finished sessions", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A heads-up when a session has finished or had to stop."
            },
        )
    }

    fun progress(state: RunState.Running): Notification {
        val total = state.plannedFrames
        val title =
            if (total !=
                null
            ) {
                "${state.name}: frame ${state.frames} of $total"
            } else {
                "${state.name}: ${state.frames} frames"
            }
        val remainingMs = total?.let { (it - state.frames).coerceAtLeast(0) * state.intervalMs }
        val text =
            when {
                state.stopping -> "Stopping…"
                state.notices.isNotEmpty() -> state.notices.last()
                remainingMs != null -> "About ${IntervalMath.formatSeconds(remainingMs)} left"
                else -> "Runs until you stop it"
            }
        val builder =
            NotificationCompat
                .Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentIntent(openApp())
                .addAction(0, "Stop", stopIntent())
        if (total != null) builder.setProgress(total, state.frames.coerceAtMost(total), false)
        return builder.build()
    }

    fun update(state: RunState.Running) {
        manager.notify(ONGOING_ID, progress(state))
    }

    fun finished(
        name: String,
        status: SessionStatus,
        frames: Int,
        message: String?,
    ) {
        val title =
            when (status) {
                SessionStatus.COMPLETED -> "$name finished: $frames frames"
                SessionStatus.STOPPED -> "$name stopped: $frames frames kept"
                else -> "$name ended early: $frames frames kept"
            }
        manager.notify(
            DONE_ID,
            NotificationCompat
                .Builder(context, DONE_CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(title)
                .setContentText(message ?: "Open Siderea to review or export it.")
                .setAutoCancel(true)
                .setContentIntent(openApp())
                .build(),
        )
    }

    private fun openApp(): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun stopIntent(): PendingIntent =
        PendingIntent.getService(
            context,
            1,
            Intent(context, TimelapseService::class.java).setAction(TimelapseService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    companion object {
        const val ONGOING_ID = 4201
        private const val DONE_ID = 4202
        private const val CHANNEL = "capture_session"
        private const val DONE_CHANNEL = "capture_done"
    }
}
