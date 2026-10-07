package io.github.mrdarkdebug.siderea.ui.gallery

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class GalleryPhoto(
    val uri: String,
    val name: String,
    val capturedAt: Long,
    val bytes: Long,
    val width: Int = 0,
    val height: Int = 0,
    val mime: String = "image/jpeg",
    val sessionId: String? = null,
    val exposureNs: Long? = null,
    val iso: Int? = null,
    val sessionExport: Boolean = false,
) {
    val editable: Boolean get() = mime == "image/jpeg" || mime == "image/png"

    fun date(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(capturedAt).atZone(zone).toLocalDate()
}

fun galleryDays(
    photos: List<GalleryPhoto>,
    zone: ZoneId = ZoneId.systemDefault(),
): Map<LocalDate, List<GalleryPhoto>> = photos.sortedByDescending { it.capturedAt }.groupBy { it.date(zone) }

/** Monday-first cells, including blank cells before the first day. */
fun calendarCells(month: YearMonth): List<LocalDate?> =
    List(month.atDay(1).dayOfWeek.value - 1) { null } + (1..month.lengthOfMonth()).map(month::atDay)
