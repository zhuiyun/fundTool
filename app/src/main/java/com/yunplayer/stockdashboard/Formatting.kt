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

fun marketSummaryItems(indexes: List<IndexImpact>): List<IndexImpact> {
    // 单行横向滑动展示全部指数，仅把"汇率"统一移到末位
    val exchangeRate = indexes.firstOrNull { it.name.contains("汇率") } ?: return indexes
    return indexes.filterNot { it === exchangeRate } + exchangeRate
}

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

private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
