package io.github.mrdarkdebug.siderea.capture

import android.app.AlarmManager
import android.os.Build

/** Exact alarms need a user-granted permission from Android 12; before that they are always allowed. */
internal fun AlarmManager.canUseExactAlarms(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S || canScheduleExactAlarms()
