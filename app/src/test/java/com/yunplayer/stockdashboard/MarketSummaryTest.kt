package com.yunplayer.stockdashboard

import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class MarketSummaryTest {
    @Test
    fun marketSummaryKeepsFourWebsiteCardsIncludingExchangeRate() {
        val indexes = listOf(
            IndexImpact("纳指100ETF(QQQ)", "0.54%"),
            IndexImpact("标普500ETF(SPY)", "0.08%"),
            IndexImpact("道琼斯", "0.11%"),
            IndexImpact("汇率", "-0.02%")
        )

        assertEquals(
            listOf("纳指100ETF(QQQ)", "标普500ETF(SPY)", "道琼斯", "汇率"),
            marketSummaryItems(indexes).map { it.name }
        )
    }

    @Test
    fun marketSummaryDoesNotDuplicateExchangeRate() {
        val indexes = listOf(
            IndexImpact("汇率", "-0.02%"),
            IndexImpact("纳指100ETF(QQQ)", "0.54%"),
            IndexImpact("标普500ETF(SPY)", "0.08%")
        )

        assertEquals(1, marketSummaryItems(indexes).count { it.name == "汇率" })
    }

    @Test
    fun marketSummaryShowsAllItemsWithExchangeRateLast() {
        val indexes = listOf(
            IndexImpact("纳斯达克", "-1.34%"),
            IndexImpact("纳斯达克100", "-0.99%"),
            IndexImpact("标普500", "-0.84%"),
            IndexImpact("道琼斯", "-0.21%"),
            IndexImpact("汇率", "-0.02%")
        )

        assertEquals(
            listOf("纳斯达克", "纳斯达克100", "标普500", "道琼斯", "汇率"),
            marketSummaryItems(indexes).map { it.name }
        )
    }

    @Test
    fun formatsGoldQuoteValues() {
        assertEquals("¥788.12", formatGoldPrice(788.12))
        assertEquals("+8.12", formatSignedNumber(8.12))
        assertEquals("-5.00", formatSignedNumber(-5.0))
        assertEquals("10:00:30", formatClockTime(36_030_000L, ZoneOffset.UTC))
    }
}
