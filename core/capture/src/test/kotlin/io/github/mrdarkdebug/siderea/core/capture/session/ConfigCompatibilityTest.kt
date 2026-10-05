package io.github.mrdarkdebug.siderea.core.capture.session

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sessions written by older versions must still open: new fields have to default sensibly. */
class ConfigCompatibilityTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun aConfigFromBeforeTheRampStillLoadsAndDoesNotRamp() {
        val old =
            """{"intervalMs":5000,"stop":"FRAME_COUNT","frameCount":300,"lockExposure":true,""" +
                """"outputFps":30,"adaptToHeat":true}"""
        val config = json.decodeFromString(TimelapseConfig.serializer(), old)
        assertFalse(config.rampExposure)
        assertEquals(TimelapseConfig.DEFAULT_RAMP_MAX_ISO, config.rampMaxIso)
        assertTrue(config.lockExposure)
        assertEquals(300, config.plannedFrames)
    }

    @Test
    fun theRampSettingsRoundTrip() {
        val config =
            TimelapseConfig(
                intervalMs = 10_000,
                stop = StopCondition.UNTIL_STOPPED,
                rampExposure = true,
                rampMaxIso = 800,
            )
        val back =
            json.decodeFromString(
                TimelapseConfig.serializer(),
                json.encodeToString(TimelapseConfig.serializer(), config),
            )
        assertEquals(config, back)
    }
}
