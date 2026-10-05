package io.github.mrdarkdebug.siderea.device

import android.view.KeyEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceTest {
    @Test
    fun `storage is shown in gigabytes or megabytes`() {
        assertEquals("41 GB", DeviceStatus.formatBytes(41_000_000_000))
        assertEquals("4.9 GB", DeviceStatus.formatBytes(4_900_000_000))
        assertEquals("820 MB", DeviceStatus.formatBytes(820_000_000))
    }

    @Test
    fun `volume and camera keys are shutter keys`() {
        listOf(
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_CAMERA,
            KeyEvent.KEYCODE_HEADSETHOOK,
        ).forEach { assertTrue(ShutterKeyBus.isShutterKey(it)) }
        assertFalse(ShutterKeyBus.isShutterKey(KeyEvent.KEYCODE_BACK))
        assertFalse(ShutterKeyBus.isShutterKey(KeyEvent.KEYCODE_A))
    }

    @Test
    fun `keys are left alone unless the camera screen wants them`() {
        val bus = ShutterKeyBus()
        assertFalse(bus.onKeyDown(KeyEvent.KEYCODE_VOLUME_UP, 0))
        bus.enabled = true
        assertTrue(bus.onKeyDown(KeyEvent.KEYCODE_VOLUME_UP, 0))
        assertFalse("other keys still pass through", bus.onKeyDown(KeyEvent.KEYCODE_BACK, 0))
    }

    @Test
    fun `a held key takes one photo, not a burst`() =
        runTest(UnconfinedTestDispatcher()) {
            val bus = ShutterKeyBus()
            bus.enabled = true
            val received = mutableListOf<Unit>()
            val job = launch { bus.presses.toList(received) }
            bus.onKeyDown(KeyEvent.KEYCODE_VOLUME_DOWN, 0)
            bus.onKeyDown(KeyEvent.KEYCODE_VOLUME_DOWN, 1)
            bus.onKeyDown(KeyEvent.KEYCODE_VOLUME_DOWN, 2)
            job.cancel()
            assertEquals(1, received.size)
        }
}
