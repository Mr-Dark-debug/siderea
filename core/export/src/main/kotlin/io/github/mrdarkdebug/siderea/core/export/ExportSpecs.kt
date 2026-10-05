package io.github.mrdarkdebug.siderea.core.export

/** Video formats Siderea can ask the phone's hardware encoder for. */
enum class VideoCodec(
    val mime: String,
    val label: String,
    /** Rough bits per pixel per frame that look clean on timelapse material. */
    val bitsPerPixel: Double,
) {
    H264("video/avc", "H.264", 0.20),
    HEVC("video/hevc", "H.265", 0.12),

    /** Only offered when the phone has a hardware AV1 encoder: a software one is far too slow. */
    AV1("video/av01", "AV1", 0.09),
}

/** The longest side of the output video, never larger than the source. */
enum class OutputSize(
    val label: String,
    val longSide: Int?,
) {
    SOURCE("Source", null),
    UHD("4K", 3840),
    FHD("1080p", 1920),
    HD("720p", 1280),
}

/** What shape to cut the frame to before it becomes video. */
enum class CropAspect(
    val label: String,
    val width: Int?,
    val height: Int?,
) {
    FULL("Full frame", null, null),
    WIDE("16:9", 16, 9),
    STANDARD("4:3", 4, 3),
    SQUARE("1:1", 1, 1),
    TALL("9:16", 9, 16),
}

/** How hard to even out frame-to-frame brightness flicker. The window is the number of neighbours each side. */
enum class DeflickerLevel(
    val label: String,
    val window: Int,
) {
    OFF("Off", 0),
    LIGHT("Light", 2),
    MEDIUM("Medium", 5),
    STRONG("Strong", 10),
}

/** Multiplies the bitrate. */
enum class VideoQuality(
    val label: String,
    val factor: Double,
) {
    DRAFT("Draft", 0.5),
    GOOD("Good", 1.0),
    BEST("Best", 1.8),
}

data class VideoSpec(
    val codec: VideoCodec = VideoCodec.H264,
    val fps: Int = DEFAULT_FPS,
    val size: OutputSize = OutputSize.FHD,
    val crop: CropAspect = CropAspect.FULL,
    /** 0..1: where the crop window sits when the frame is larger than it. 0.5 is centred. */
    val panX: Float = CENTRE,
    val panY: Float = CENTRE,
    val deflicker: DeflickerLevel = DeflickerLevel.OFF,
    val quality: VideoQuality = VideoQuality.GOOD,
) {
    companion object {
        const val DEFAULT_FPS = 30
        const val CENTRE = 0.5f
        val FPS_CHOICES = listOf(12, 24, 25, 30, 60)
    }
}

/** An integer rectangle: left/top inclusive, right/bottom exclusive. */
data class PixelRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

data class PixelSize(
    val width: Int,
    val height: Int,
)
