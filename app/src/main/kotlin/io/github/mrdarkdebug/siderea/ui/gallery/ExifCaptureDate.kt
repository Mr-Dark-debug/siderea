package io.github.mrdarkdebug.siderea.ui.gallery

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

internal object ExifCaptureDate {
    private val format = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.ROOT)
    private const val MILLIS_DIGITS = 3

    fun parse(
        date: String?,
        offset: String?,
        subsecond: String?,
        fallbackZone: ZoneId = ZoneId.systemDefault(),
    ): Long? =
        runCatching {
            if (date.isNullOrBlank()) return null
            val local = LocalDateTime.parse(date, format)
            val instant =
                if (offset.isNullOrBlank()) {
                    local
                        .atZone(
                            fallbackZone,
                        ).toInstant()
                } else {
                    local.toInstant(ZoneOffset.of(offset))
                }
            val millis = subsecond?.take(MILLIS_DIGITS)?.padEnd(MILLIS_DIGITS, '0')?.toLongOrNull() ?: 0L
            instant.toEpochMilli() + millis
        }.getOrNull()
}
