package io.github.mrdarkdebug.siderea.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Runs the repository against an in-memory [DataStore]. DataStore's file replacement uses
 * `File.renameTo`, which fails on Windows hosts when the target exists, so on-disk persistence is
 * covered by the emulator instrumentation tests instead of by host JVM tests.
 */
class SettingsRepositoryTest {
    private class InMemoryStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val next = transform(state.value)
            state.value = next
            return next
        }
    }

    @Test
    fun `defaults apply on a fresh install`() =
        runTest {
            val settings = SettingsRepository(InMemoryStore()).settings.first()
            assertEquals(AppSettings(), settings)
            assertFalse(settings.redMode)
            assertTrue(settings.hapticsEnabled)
        }

    @Test
    fun `red mode and haptics persist`() =
        runTest {
            val repo = SettingsRepository(InMemoryStore())
            repo.setRedMode(true)
            repo.setHapticsEnabled(false)
            assertEquals(AppSettings(redMode = true, hapticsEnabled = false), repo.settings.first())
        }

    @Test
    fun `a second repository over the same store sees earlier writes`() =
        runTest {
            val store = InMemoryStore()
            SettingsRepository(store).setRedMode(true)
            assertTrue(SettingsRepository(store).settings.first().redMode)
        }

    @Test
    fun `reset restores defaults`() =
        runTest {
            val repo = SettingsRepository(InMemoryStore())
            repo.setRedMode(true)
            repo.setHapticsEnabled(false)
            repo.reset()
            assertEquals(AppSettings(), repo.settings.first())
        }

    @Test
    fun `an unreadable settings file falls back to defaults instead of crashing`() =
        runTest {
            val broken =
                object : DataStore<Preferences> {
                    override val data: Flow<Preferences> = flow { throw IOException("corrupt file") }

                    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
                        error("not used")
                }
            assertEquals(AppSettings(), SettingsRepository(broken).settings.first())
        }
}
