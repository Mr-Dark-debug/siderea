package io.github.mrdarkdebug.siderea.core.camera.capability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityJsonTest {
    private val report =
        Samples.report(
            Samples.proCamera("0").copy(
                features = FeatureVerdicts.evaluate(Samples.proCamera("0"), 36),
                lens = Samples.proCamera().lens.copy(zoomLabel = "1x", zoomRatioToMain = 1f),
            ),
            Samples.basicCamera("1").copy(error = "Could not read this camera."),
        )

    @Test
    fun `report survives a JSON round trip unchanged`() {
        val decoded = CapabilityJson.decode(CapabilityJson.encode(report))
        assertEquals(report, decoded)
    }

    @Test
    fun `defaults are written so the schema is stable`() {
        val json = CapabilityJson.encode(report)
        assertTrue(json.contains("\"schemaVersion\": ${CapabilityReport.SCHEMA_VERSION}"))
        assertTrue(json.contains("\"concurrentCameraSets\""))
        assertTrue(json.contains("\"requestKeys\""))
    }

    @Test
    fun `unknown keys from a newer schema are ignored`() {
        val withExtra = CapabilityJson.encode(report).replaceFirst("{", "{\n  \"someFutureField\": 42,")
        assertEquals(report, CapabilityJson.decode(withExtra))
    }

    @Test
    fun `output is human readable`() {
        assertTrue(CapabilityJson.encode(report).lines().size > 50)
    }
}
