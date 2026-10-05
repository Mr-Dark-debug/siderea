package io.github.mrdarkdebug.siderea.device

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import javax.inject.Inject
import javax.inject.Singleton

/** A snapshot of the phone's battery, free space and heat, shown in the viewfinder's status line. */
data class DeviceStatus(
    val batteryPercent: Int?,
    val charging: Boolean,
    /** Free space where photos are saved, in bytes, or null if it can't be read. */
    val freeBytes: Long?,
    /** `PowerManager.THERMAL_STATUS_*` (0 none … 6 shutdown), or null if unknown. */
    val thermalStatus: Int?,
) {
    val thermalWarning: Boolean get() = (thermalStatus ?: 0) >= PowerManager.THERMAL_STATUS_MODERATE

    companion object {
        val UNKNOWN = DeviceStatus(null, charging = false, freeBytes = null, thermalStatus = null)

        /** "41 GB", "820 MB". */
        fun formatBytes(bytes: Long): String {
            val gb = bytes / GIGA
            return if (gb >= 1) {
                "${"%.1f".format(java.util.Locale.ROOT, bytes / GIGA)} GB".replace(".0 GB", " GB")
            } else {
                "${bytes / MEGA} MB"
            }
        }

        private const val GIGA = 1_000_000_000.0
        private const val MEGA = 1_000_000L
    }
}

@Singleton
class DeviceStatusReader
    @Inject
    constructor(
        @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    ) {
        fun read(): DeviceStatus {
            val battery: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val percent = if (level >= 0 && scale > 0) level * PERCENT / scale else null
            val charging =
                status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            val thermal = context.getSystemService(PowerManager::class.java)?.currentThermalStatus
            return DeviceStatus(percent, charging, freeBytes(), thermal)
        }

        private fun freeBytes(): Long? =
            runCatching {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                StatFs((if (dir.exists()) dir else Environment.getExternalStorageDirectory()).path).availableBytes
            }.getOrNull()

        private companion object {
            const val PERCENT = 100
        }
    }
