package com.yunplayer.stockdashboard

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

class GoldRepositoryTest {
    @Test
    fun fetchLatestParsesPriceAndCalculatesChange() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "resultData": {
                    "datas": {
                      "price": "788.12",
                      "time": "10:20:30",
                      "yesterdayPrice": "780.00",
                      "upAndDownRate": "1.04%"
                    },
                    "status": "0"
                  },
                  "success": true
                }
                """.trimIndent()
            )
        )
        server.start()

        try {
            val repository = GoldRepository(
                client = OkHttpClient(),
                endpoint = server.url("/latestPrice?reqData={}").toString(),
                nowMillis = { 1_718_700_000_000L }
            )

            val quote = repository.fetchLatest()
            val request = server.takeRequest()

            assertEquals("/latestPrice?reqData={}", request.path)
            assertEquals(788.12, quote.price, 0.0)
            assertEquals(780.0, quote.yesterdayPrice, 0.0)
            assertEquals(8.12, quote.changeAmount, 0.0001)
            assertEquals(1.04, quote.changePercent, 0.0)
            assertEquals(1_718_700_000_000L, quote.updatedAtMillis)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun fetchLatestCalculatesPercentageWhenApiRateIsMissing() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                """{"resultData":{"datas":{"price":"760","yesterdayPrice":"800"}},"success":true}"""
            )
        )
        server.start()

        try {
            val repository = GoldRepository(
                client = OkHttpClient(),
                endpoint = server.url("/latest").toString()
            )

            val quote = repository.fetchLatest()

            assertEquals(-40.0, quote.changeAmount, 0.0)
            assertEquals(-5.0, quote.changePercent, 0.0)
        } finally {
            server.shutdown()
        }
    }
}
