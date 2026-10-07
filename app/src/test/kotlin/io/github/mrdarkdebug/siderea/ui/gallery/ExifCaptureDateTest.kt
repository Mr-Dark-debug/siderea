package io.github.mrdarkdebug.siderea.ui.gallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ExifCaptureDateTest {
    @Test fun explicitOffsetRetainsInstantAndMillisecondsAcrossCalendarDays() {
        assertEquals(
            Instant.parse("2024-03-10T23:30:45.123Z").toEpochMilli(),
            ExifCaptureDate.parse("2024:03:11 00:30:45", "+01:00", "123"),
        )
    }

    @Test fun offsetlessLegacyPhotoUsesCaptureDatesZoneRules() {
        assertEquals(
            Instant.parse("2024-03-10T23:30:45Z").toEpochMilli(),
            ExifCaptureDate.parse("2024:03:11 00:30:45", null, null, ZoneId.of("Europe/Berlin")),
        )
    }

    @Test fun fractionalSecondsPadOrTruncateToMilliseconds() {
        val base = Instant.parse("2024-03-11T00:30:45Z").toEpochMilli()
        assertEquals(base + 100, ExifCaptureDate.parse("2024:03:11 00:30:45", "+00:00", "1"))
        assertEquals(base + 123, ExifCaptureDate.parse("2024:03:11 00:30:45", "+00:00", "123456"))
    }

    @Test fun absentOrMalformedExifFallsBackToMediaAddedDate() {
        assertNull(ExifCaptureDate.parse(null, null, null))
        assertNull(ExifCaptureDate.parse("invalid", "+00:00", null))
        assertNull(ExifCaptureDate.parse("2024:03:11 00:30:45", "+30:00", null))
    }
}
