package io.github.mrdarkdebug.siderea.core.camera.capability

import kotlinx.serialization.json.Json

/** JSON encoding of a [CapabilityReport]: what "Copy as JSON" and "Share report" produce. */
object CapabilityJson {
    private val json =
        Json {
            prettyPrint = true
            // Keep the schema stable: a field with its default value still appears in the output.
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

    fun encode(report: CapabilityReport): String = json.encodeToString(CapabilityReport.serializer(), report)

    fun decode(text: String): CapabilityReport = json.decodeFromString(CapabilityReport.serializer(), text)
}
