package io.github.mrdarkdebug.siderea.device

import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Carries hardware shutter presses (volume keys, and the volume or camera keys that Bluetooth remotes and
 * selfie sticks send) from the activity to whichever screen is the camera. Only consumes keys while the
 * camera screen says it wants them, so the volume keys work normally everywhere else.
 */
@Singleton
class ShutterKeyBus
    @Inject
    constructor() {
        private val mutablePresses = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val presses: SharedFlow<Unit> = mutablePresses.asSharedFlow()

        @Volatile
        var enabled: Boolean = false

        /** @return true if the key was a shutter key and was taken. */
        fun onKeyDown(
            keyCode: Int,
            repeatCount: Int,
        ): Boolean {
            if (!enabled || !isShutterKey(keyCode)) return false
            // A held key must not take a burst of photos.
            if (repeatCount == 0) mutablePresses.tryEmit(Unit)
            return true
        }

        companion object {
            fun isShutterKey(keyCode: Int): Boolean =
                keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
                    keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
                    keyCode == KeyEvent.KEYCODE_CAMERA ||
                    keyCode == KeyEvent.KEYCODE_HEADSETHOOK
        }
    }
