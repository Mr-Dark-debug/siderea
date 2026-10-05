package io.github.mrdarkdebug.siderea.core.camera.control

/**
 * What the live preview runs at while a manual exposure is set.
 *
 * A 16 s shutter would make the viewfinder update once every 16 s. Instead the preview runs at a capped
 * shutter with ISO raised to keep the same brightness, and any brightness the sensor can't deliver is
 * made up by [displayGain], applied to the on-screen image only. The saved photo always uses the exposure
 * the user chose.
 */
data class PreviewExposure(
    val shutterNs: Long,
    val iso: Int,
    /** Brightness multiplier to apply when drawing the preview (1 = none). */
    val displayGain: Float,
) {
    companion object {
        /** The preview refreshes at least four times a second. */
        const val MAX_PREVIEW_SHUTTER_NS = 250_000_000L
        const val MAX_DISPLAY_GAIN = 16f

        fun forManual(
            shutterNs: Long,
            iso: Int,
            limits: ExposureLimits,
            maxPreviewShutterNs: Long = MAX_PREVIEW_SHUTTER_NS,
        ): PreviewExposure {
            if (shutterNs <= maxPreviewShutterNs) return PreviewExposure(shutterNs, iso, displayGain = 1f)
            val previewShutter = maxPreviewShutterNs.coerceAtLeast(limits.shutterMinNs)
            val wantedIso = iso.toDouble() * shutterNs / previewShutter
            val previewIso = wantedIso.toInt().coerceIn(limits.isoMin, limits.isoMax)
            val delivered = previewShutter.toDouble() * previewIso
            val wanted = shutterNs.toDouble() * iso
            val gain = (wanted / delivered).toFloat().coerceIn(1f, MAX_DISPLAY_GAIN)
            return PreviewExposure(previewShutter, previewIso, gain)
        }
    }
}
