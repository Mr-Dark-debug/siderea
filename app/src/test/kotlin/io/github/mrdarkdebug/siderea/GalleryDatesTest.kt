package io.github.mrdarkdebug.siderea

import io.github.mrdarkdebug.siderea.ui.gallery.GalleryPhoto
import io.github.mrdarkdebug.siderea.ui.gallery.calendarCells
import io.github.mrdarkdebug.siderea.ui.gallery.galleryDays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class GalleryDatesTest {
    @Test fun datesUseLocalCaptureTimeAndNewestFirst() {
        val older = photo("a", "2026-10-04T21:59:00Z")
        val newer = photo("b", "2026-10-04T22:01:00Z")
        val days = galleryDays(listOf(older, newer), ZoneId.of("Europe/Berlin"))
        assertEquals(listOf(LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-04")), days.keys.toList())
        assertEquals(newer, days.values.first().single())
    }

    @Test fun leapYearAndMondayStartHaveCorrectCells() {
        val feb = calendarCells(YearMonth.of(2024, 2))
        assertEquals(3, feb.takeWhile { it == null }.size)
        assertEquals(LocalDate.of(2024, 2, 29), feb.last())
        assertEquals(28, calendarCells(YearMonth.of(2026, 2)).count { it != null })
        assertEquals(LocalDate.of(2026, 6, 1), calendarCells(YearMonth.of(2026, 6)).first())
    }

    @Test fun emptyGalleryHasNoInventedDates() {
        assertTrue(galleryDays(emptyList()).isEmpty())
    }

    private fun photo(
        id: String,
        time: String,
    ) = GalleryPhoto(id, "$id.jpg", Instant.parse(time).toEpochMilli(), 10)
}
