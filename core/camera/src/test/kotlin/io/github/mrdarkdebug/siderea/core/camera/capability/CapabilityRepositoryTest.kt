package io.github.mrdarkdebug.siderea.core.camera.capability

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityRepositoryTest {
    private class CountingSource(
        private val outcome: () -> CapabilityReport,
    ) : CapabilitySource {
        var reads = 0

        override suspend fun read(): CapabilityReport {
            reads++
            return outcome()
        }
    }

    private val report = Samples.report(Samples.proCamera())

    @Test
    fun `starts in Loading`() {
        val repo = CapabilityRepository(CountingSource { report })
        assertEquals(CapabilityState.Loading, repo.state.value)
    }

    @Test
    fun `ensureLoaded reads once and then reuses the report`() =
        runTest {
            val source = CountingSource { report }
            val repo = CapabilityRepository(source)
            repo.ensureLoaded()
            repo.ensureLoaded()
            assertEquals(1, source.reads)
            assertEquals(CapabilityState.Ready(report), repo.state.value)
        }

    @Test
    fun `refresh always reads again`() =
        runTest {
            val source = CountingSource { report }
            val repo = CapabilityRepository(source)
            repo.ensureLoaded()
            repo.refresh()
            assertEquals(2, source.reads)
        }

    @Test
    fun `a security failure becomes a human-readable Failed state`() =
        runTest {
            val repo = CapabilityRepository(CountingSource { throw SecurityException("camera disabled by policy") })
            repo.ensureLoaded()
            val state = repo.state.value
            assertTrue(state is CapabilityState.Failed)
            assertTrue((state as CapabilityState.Failed).message.contains("camera disabled by policy"))
        }

    @Test
    fun `a failed read can be retried`() =
        runTest {
            var fail = true
            val repo =
                CapabilityRepository(
                    CountingSource { if (fail) error("camera busy") else report },
                )
            repo.ensureLoaded()
            assertTrue(repo.state.value is CapabilityState.Failed)
            fail = false
            repo.ensureLoaded()
            assertEquals(CapabilityState.Ready(report), repo.state.value)
        }
}
