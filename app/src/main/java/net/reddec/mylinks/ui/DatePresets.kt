package net.reddec.mylinks.ui

import net.reddec.mylinks.data.DateFilter
import net.reddec.mylinks.data.endOfDayMillis
import net.reddec.mylinks.data.startOfDay
import net.reddec.mylinks.data.startOfDayMillis
import net.reddec.mylinks.data.startOfYear
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Quick date filters for "search by date". */
fun datePresets(now: Long = System.currentTimeMillis()): List<DateFilter> = listOf(
    DateFilter("Today", startOfDay(0, now), null),
    DateFilter("Last 7 days", startOfDay(6, now), null),
    DateFilter("Last 30 days", startOfDay(29, now), null),
    DateFilter("This year", startOfYear(now), null),
    DateFilter("All time", null, null),
)

fun shortDate(millis: Long): String =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))

fun rangeFilter(fromMillis: Long, toMillis: Long): DateFilter = DateFilter(
    label = "${shortDate(fromMillis)} – ${shortDate(toMillis)}",
    from = startOfDayMillis(fromMillis),
    to = endOfDayMillis(toMillis),
)
