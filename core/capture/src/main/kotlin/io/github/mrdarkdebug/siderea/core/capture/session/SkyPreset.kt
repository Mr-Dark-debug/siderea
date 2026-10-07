package io.github.mrdarkdebug.siderea.core.capture.session

import kotlinx.serialization.Serializable

@Serializable
enum class SkyPreset(
    val label: String,
    val hint: String,
    val exposureNs: Long,
    val iso: Int,
    val frames: Int,
) {
    NIGHT_SKY("Night sky", "Stars and constellations", 8_000_000_000L, 1600, 20),
    MILKY_WAY("Milky Way", "A dark sky away from city lights", 15_000_000_000L, 3200, 40),
    STAR_TRAILS("Star trails", "Trace the stars as Earth turns", 20_000_000_000L, 800, 100),
    MOON("Moon", "A short exposure for a bright moon", 4_000_000L, 100, 20),
}
