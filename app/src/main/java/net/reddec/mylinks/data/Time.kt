package net.reddec.mylinks.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Shiori stores timestamps as UTC strings. Depending on the database backend the JSON can be
 * "2024-11-09 12:04:32" (SQLite), "2024-11-09T12:04:32Z" or "+00:00" flavoured (Postgres).
 * Anything unparseable falls back to [now].
 */
fun parseServerTime(raw: String?, now: Long = System.currentTimeMillis()): Long {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return now

    parseOrNull { OffsetDateTime.parse(text).toInstant().toEpochMilli() }?.let { return it }
    parseOrNull { Instant.parse(text).toEpochMilli() }?.let { return it }
    parseOrNull { LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).toInstant(ZoneOffset.UTC).toEpochMilli() }?.let { return it }
    parseOrNull { LocalDateTime.parse(text).toInstant(ZoneOffset.UTC).toEpochMilli() }?.let { return it }
    parseOrNull { LocalDate.parse(text).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }?.let { return it }
    return now
}

private inline fun parseOrNull(block: () -> Long): Long? = try {
    block()
} catch (_: Exception) {
    null
}

private val zone: ZoneId get() = ZoneId.systemDefault()

private fun dateTime(millis: Long): LocalDateTime = Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()

/** "now", "7m", "3h", "Yesterday", "9 Nov", "9 Nov 24" — calendar days win over raw hours. */
fun relativeTime(millis: Long, now: Long = System.currentTimeMillis()): String {
    if (millis <= 0L) return ""
    val diff = now - millis
    if (diff < 60_000L) return "now"

    val day = dateTime(millis).toLocalDate()
    val today = dateTime(now).toLocalDate()

    if (day == today) {
        if (diff < 3_600_000L) return "${diff / 60_000L}m"
        return "${diff / 3_600_000L}h"
    }
    if (day == today.minusDays(1)) return "Yesterday"
    val pattern = if (day.year == today.year) "d MMM" else "d MMM yy"
    return day.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}

/** Group header for the library list: TODAY / YESTERDAY / 9 NOVEMBER / 9 NOVEMBER 2024 */
fun dayHeader(millis: Long, now: Long = System.currentTimeMillis()): String {
    val day = dateTime(millis).toLocalDate()
    val today = dateTime(now).toLocalDate()
    return when {
        day == today -> "TODAY"
        day == today.minusDays(1) -> "YESTERDAY"
        day.year == today.year -> day.format(DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault())).uppercase(Locale.getDefault())
        else -> day.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault())).uppercase(Locale.getDefault())
    }
}

fun fullDate(millis: Long): String =
    dateTime(millis).format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.getDefault()))

/** "example.com" for a URL, used as the editorial standfirst. */
fun hostOf(url: String): String = try {
    val host = java.net.URI(url).host ?: url
    host.removePrefix("www.")
} catch (_: Exception) {
    url.removePrefix("https://").removePrefix("http://").substringBefore('/')
}

/** Start of the local day [daysAgo] days back, in epoch millis. */
fun startOfDay(daysAgo: Int = 0, now: Long = System.currentTimeMillis()): Long =
    dateTime(now).toLocalDate().minusDays(daysAgo.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()

fun startOfYear(now: Long = System.currentTimeMillis()): Long =
    dateTime(now).toLocalDate().withDayOfYear(1).atStartOfDay(zone).toInstant().toEpochMilli()

fun startOfDayMillis(millis: Long): Long =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

fun endOfDayMillis(millis: Long): Long = startOfDayMillis(millis) + 86_400_000L
