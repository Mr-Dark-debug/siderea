package io.github.mrdarkdebug.siderea.capture

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.core.content.ContextCompat
import io.github.mrdarkdebug.siderea.core.capture.timelapse.RunnerClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The runner's clock on a real phone.
 *
 * Short waits use a plain coroutine delay, which is exact while the CPU is awake (the screen is on, or a
 * wake lock is honoured). Longer waits also set an `AlarmManager` alarm, because a phone left alone on a
 * tripod falls into Doze, and Doze suspends the CPU and with it every coroutine timer. An exact
 * "allow while idle" alarm wakes it on time; if the user hasn't granted exact alarms, an inexact
 * allow-while-idle alarm is the best Android permits.
 */
class AndroidRunnerClock(
    private val context: Context,
    /** Waits shorter than this don't bother with an alarm. */
    private val alarmThresholdMs: Long = ALARM_THRESHOLD_MS,
) : RunnerClock {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    override fun nowMs(): Long = SystemClock.elapsedRealtime()

    override suspend fun delayUntil(targetMs: Long) {
        val remaining = targetMs - nowMs()
        if (remaining <= 0) return
        if (remaining < alarmThresholdMs) {
            delay(remaining)
            return
        }
        val wake = CompletableDeferred<Unit>()
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    wake.complete(Unit)
                }
            }
        val action = ACTION_PREFIX + targetMs
        ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
        val intent =
            PendingIntent.getBroadcast(
                context,
                0,
                Intent(action).setPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        try {
            schedule(targetMs, intent)
            withTimeoutOrNull(remaining) { wake.await() }
        } finally {
            alarms.cancel(intent)
            runCatching { context.unregisterReceiver(receiver) }
        }
        // If an alarm woke the phone slightly early, finish the remainder precisely.
        val rest = targetMs - nowMs()
        if (rest > 0) delay(rest)
    }

    private fun schedule(
        targetMs: Long,
        intent: PendingIntent,
    ) {
        if (alarms.canUseExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, targetMs, intent)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, targetMs, intent)
        }
    }

    private companion object {
        const val ALARM_THRESHOLD_MS = 20_000L
        const val ACTION_PREFIX = "io.github.mrdarkdebug.siderea.FRAME_ALARM_"
    }
}
