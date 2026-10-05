package io.github.mrdarkdebug.siderea.core.processing

/** Why a frame did not make it into a stack. */
enum class RejectReason(
    val label: String,
) {
    UNREADABLE("could not be read"),
    WRONG_SIZE("has a different size"),
    TOO_FEW_STARS("shows too few stars"),
    NO_MATCH("does not line up with the reference"),
}

data class RejectedFrame(
    val index: Int,
    val reason: RejectReason,
)

data class StackResult(
    val accumulator: StackAccumulator,
    /** Frames that were aligned and added, including the reference. */
    val used: Int,
    val rejected: List<RejectedFrame>,
    val referenceIndex: Int,
    /** Worst shift and rotation among the used frames, for the report. */
    val maxShiftPixels: Double,
    val maxRotationDegrees: Double,
    val meanRmsError: Double,
)

/** Turns a session's frames into a star-trail image or an aligned stack. All pixel work is plain Kotlin. */
object AstroProcessor {
    private const val REFERENCE_CANDIDATES = 5
    private const val MIN_STARS = 6

    /**
     * Star trails. With [cometFade] below 1 older light dims each frame, which makes comet-like tails. Dark
     * subtraction ([dark]) is applied to each frame first. Frames of the wrong size or unreadable are skipped.
     */
    fun trails(
        source: FrameSource,
        dark: MasterDark? = null,
        cometFade: Float = 1f,
        checkCancelled: () -> Unit = {},
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): RgbImage? {
        var accumulator: TrailAccumulator? = null
        for (index in 0 until source.count) {
            checkCancelled()
            val frame = source.load(index)
            if (frame != null) {
                val acc =
                    accumulator ?: TrailAccumulator(frame.width, frame.height, cometFade).also { accumulator = it }
                if (frame.width == acc.width && frame.height == acc.height) {
                    acc.add(dark?.subtractFrom(frame) ?: frame)
                }
            }
            onProgress(index + 1, source.count)
        }
        return accumulator?.toRgbImage()
    }

    /**
     * Aligned stacking: picks the reference among the first few frames (the one with the most stars), finds each
     * frame's transform to it, and averages. Frames that cannot be matched are left out and reported, never added
     * misaligned.
     */
    fun stack(
        source: FrameSource,
        dark: MasterDark? = null,
        checkCancelled: () -> Unit = {},
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): StackResult? {
        val reference = pickReference(source, dark, checkCancelled) ?: return null
        val accumulator = StackAccumulator(reference.image.width, reference.image.height)
        accumulator.add(reference.image)
        val rejected = ArrayList<RejectedFrame>()
        var used = 1
        var maxShift = 0.0
        var maxRotation = 0.0
        var errorSum = 0.0
        for (index in 0 until source.count) {
            checkCancelled()
            if (index != reference.index) {
                val outcome = alignFrame(source, index, reference, dark)
                if (outcome.result != null) {
                    val r = outcome.result
                    accumulator.add(outcome.image!!, r.transform)
                    used++
                    maxShift = maxOf(maxShift, kotlin.math.hypot(r.transform.tx, r.transform.ty))
                    maxRotation = maxOf(maxRotation, kotlin.math.abs(r.transform.rotationDegrees))
                    errorSum += r.rmsError
                } else {
                    rejected += RejectedFrame(index, outcome.reason!!)
                }
            }
            onProgress(index + 1, source.count)
        }
        return StackResult(
            accumulator,
            used,
            rejected,
            reference.index,
            maxShift,
            maxRotation,
            errorSum / used.coerceAtLeast(1),
        )
    }

    private class Reference(
        val index: Int,
        val image: RgbImage,
        val stars: List<Star>,
    )

    private class Outcome(
        val result: AlignResult?,
        val image: RgbImage?,
        val reason: RejectReason?,
    )

    private fun pickReference(
        source: FrameSource,
        dark: MasterDark?,
        checkCancelled: () -> Unit,
    ): Reference? {
        var best: Reference? = null
        var tried = 0
        var index = 0
        while (index < source.count && tried < REFERENCE_CANDIDATES) {
            checkCancelled()
            val image = source.load(index)?.let { dark?.subtractFrom(it) ?: it }
            if (image != null) {
                tried++
                val stars = StarDetector.detect(image)
                if (best == null || stars.size > best.stars.size) best = Reference(index, image, stars)
            }
            index++
        }
        return best?.takeIf { it.stars.size >= MIN_STARS }
    }

    private fun alignFrame(
        source: FrameSource,
        index: Int,
        reference: Reference,
        dark: MasterDark?,
    ): Outcome {
        val image =
            source.load(index)?.let { dark?.subtractFrom(it) ?: it }
                ?: return Outcome(null, null, RejectReason.UNREADABLE)
        if (image.width != reference.image.width || image.height != reference.image.height) {
            return Outcome(null, null, RejectReason.WRONG_SIZE)
        }
        val stars = StarDetector.detect(image)
        if (stars.size < MIN_STARS) return Outcome(null, null, RejectReason.TOO_FEW_STARS)
        val result = Alignment.estimate(reference.stars, stars) ?: return Outcome(null, null, RejectReason.NO_MATCH)
        return Outcome(result, image, null)
    }
}
