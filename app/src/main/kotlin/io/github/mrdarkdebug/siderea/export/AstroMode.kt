package io.github.mrdarkdebug.siderea.export

/** What to make from a sky session. */
enum class AstroMode(
    val label: String,
    val fileTag: String,
    val description: String,
) {
    TRAILS("Star trails", "trails", "Every star becomes a streak across the sky."),
    COMET("Comet trails", "comet", "Streaks that fade, brightest at the newest end."),
    STACK("Aligned stack", "stack", "Lines the stars up and averages the frames for a cleaner, deeper picture."),
}

/** Keeps big sessions from running out of memory by processing at a reduced size when needed. */
object AstroMemory {
    private val SAMPLES = listOf(1, 2, 3, 4, 6, 8)
    private const val STACK_BYTES_PER_PIXEL = 32L
    private const val TRAIL_BYTES_PER_PIXEL = 16L
    private const val BUDGET_FRACTION = 0.5

    /** The smallest reduction factor whose working set fits in about half the app's memory limit. */
    fun sampleFor(
        width: Int,
        height: Int,
        mode: AstroMode,
        maxMemory: Long = Runtime.getRuntime().maxMemory(),
    ): Int {
        val perPixel = if (mode == AstroMode.STACK) STACK_BYTES_PER_PIXEL else TRAIL_BYTES_PER_PIXEL
        val budget = (maxMemory * BUDGET_FRACTION).toLong()
        return SAMPLES.firstOrNull { s -> (width.toLong() / s) * (height.toLong() / s) * perPixel <= budget }
            ?: SAMPLES.last()
    }
}
