package com.yunplayer.stockdashboard

import org.junit.Assert.assertEquals
import org.junit.Test

class PayloadParserTest {
    @Test
    fun parsesDashboardPayload() {
        val dashboard = ApiPayloadParser().parseDashboard(
            """
            [{},{"success":2,"indexs":[{"inxnm":"纳指100ETF(QQQ)","rise_fall_per":"0.49%"}],"timestamp":"17:56(盘前)","description":"基于Q1季报结合年报持仓计算","hiddenOvernight":true,"category1mpacts":[{"id":1,"name":"华宝纳斯达克精选","estimatedImpact":0.44,"time":""}]},[],{}]
            """.trimIndent()
        )

        assertEquals("17:56(盘前)", dashboard.timestamp)
        assertEquals(true, dashboard.hiddenOvernight)
        assertEquals("纳指100ETF(QQQ)", dashboard.indexes.single().name)
        assertEquals(0.44, dashboard.funds.single().estimatedImpact, 0.0)
    }

    @Test
    fun parsesDetailAndHoldings() {
        val detail = ApiPayloadParser().parseDetail(
            """
            [{},{"id":1,"name":"华宝纳斯达克精选","stocks":["奈飞@@@9.32@@@-0.15","英伟达@@@9.28@@@0.34"],"estimatedImpact":0.44,"time":""},[],{}]
            """.trimIndent()
        )

        assertEquals("华宝纳斯达克精选", detail.name)
        assertEquals(2, detail.holdings.size)
        assertEquals(Holding("奈飞", 9.32, -0.15), detail.holdings.first())
    }
}
