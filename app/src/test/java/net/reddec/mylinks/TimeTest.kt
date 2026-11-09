package net.reddec.mylinks

import net.reddec.mylinks.data.dayHeader
import net.reddec.mylinks.data.parseServerTime
import net.reddec.mylinks.data.relativeTime
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

class TimeTest {

    @Before
    fun fixLocale() {
        Locale.setDefault(Locale.US)
    }

    @Test
    fun parsesSqliteTimestampAsUtc() {
        assertEquals(
            Instant.parse("2024-11-09T12:04:32Z").toEpochMilli(),
            parseServerTime("2024-11-09 12:04:32"),
        )
    }

    @Test
    fun parsesIsoTimestampsWithZone() {
        val expected = Instant.parse("2024-11-09T12:04:32Z").toEpochMilli()
        assertEquals(expected, parseServerTime("2024-11-09T12:04:32Z"))
        assertEquals(expected, parseServerTime("2024-11-09T12:04:32+00:00"))
    }

    @Test
    fun unparseableFallsBackToNow() {
        assertEquals(1234L, parseServerTime("not a date", 1234L))
        assertEquals(1234L, parseServerTime(null, 1234L))
    }

    @Test
    fun relativeTimeBuckets() {
        val now = localMillis(2024, 11, 9, 15, 0)
        assertEquals("now", relativeTime(now - 30_000, now))
        assertEquals("7m", relativeTime(now - 7 * 60_000, now))
        assertEquals("3h", relativeTime(now - 3 * 3_600_000, now))
        assertEquals("Yesterday", relativeTime(localMillis(2024, 11, 8, 20, 0), now))
        assertEquals("4 Nov", relativeTime(localMillis(2024, 11, 4, 9, 0), now))
        assertEquals("4 Nov 23", relativeTime(localMillis(2023, 11, 4, 9, 0), now))
    }

    @Test
    fun dayHeadersGroupByCalendarDay() {
        val now = localMillis(2024, 11, 9, 15, 0)
        assertEquals("TODAY", dayHeader(localMillis(2024, 11, 9, 1, 0), now))
        assertEquals("YESTERDAY", dayHeader(localMillis(2024, 11, 8, 23, 0), now))
        assertEquals("7 MARCH", dayHeader(localMillis(2024, 3, 7, 9, 0), now))
        assertEquals("7 MARCH 2023", dayHeader(localMillis(2023, 3, 7, 9, 0), now))
    }

    private fun localMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
}
