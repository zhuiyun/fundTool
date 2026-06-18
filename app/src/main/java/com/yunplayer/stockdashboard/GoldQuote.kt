package com.yunplayer.stockdashboard

data class GoldQuote(
    val price: Double,
    val yesterdayPrice: Double,
    val changeAmount: Double,
    val changePercent: Double,
    val updatedAtMillis: Long
)

interface GoldDataSource {
    suspend fun fetchLatest(): GoldQuote
}
