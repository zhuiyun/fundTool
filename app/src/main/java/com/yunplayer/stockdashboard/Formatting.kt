package com.yunplayer.stockdashboard

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun formatSignedPercent(value: Double): String {
    val prefix = if (value > 0.0) "+" else ""
    return prefix + String.format(Locale.US, "%.2f%%", value)
}

fun formatPercent(value: Double): String {
    return String.format(Locale.US, "%.2f%%", value)
}

fun parsePercentText(value: String): Double {
    return value.replace("%", "").trim().toDoubleOrNull() ?: 0.0
}

/*
fun marketSummaryItems(indexes: List<IndexImpact>): List<IndexImpact> {
    val exchangeRate = indexes.firstOrNull { it.name.contains("汇率") } ?: return indexes
    return indexes.filterNot { it === exchangeRate } + exchangeRate
}
*/

fun formatGoldPrice(value: Double): String {
    return "¥" + String.format(Locale.US, "%.2f", value)
}

fun formatSignedNumber(value: Double): String {
    val prefix = if (value > 0.0) "+" else ""
    return prefix + String.format(Locale.US, "%.2f", value)
}

fun formatClockTime(
    epochMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault()
): String {
    return TIME_FORMATTER.format(Instant.ofEpochMilli(epochMillis).atZone(zoneId))
}

fun formatPrice(value: Double): String =
    "$" + String.format(Locale.US, "%.2f", value)

fun formatIndexPrice(value: Double): String =
    String.format(Locale.US, "%,.0f", value)

fun formatVolume(v: Long): String = when {
    v >= 1_000_000_000L -> String.format(Locale.US, "%.1fB", v / 1_000_000_000.0)
    v >= 1_000_000L -> String.format(Locale.US, "%.1fM", v / 1_000_000.0)
    v >= 1_000L -> String.format(Locale.US, "%.1fK", v / 1_000.0)
    else -> v.toString()
}

fun formatEstimateTime(gztime: String): String =
    if (gztime.length >= 16) gztime.substring(5, 16) else gztime

private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
