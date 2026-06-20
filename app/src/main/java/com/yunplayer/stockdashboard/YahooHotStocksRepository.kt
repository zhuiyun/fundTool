package com.yunplayer.stockdashboard

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

interface HotStocksSource {
    suspend fun fetch(tab: StockTab): List<HotStock>
}

class YahooHotStocksRepository(
    private val client: OkHttpClient = sharedClient
) : HotStocksSource {

    private val moshi = Moshi.Builder().build()
    private val mapAdapter = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    )

    override suspend fun fetch(tab: StockTab): List<HotStock> = withContext(Dispatchers.IO) {
        val scrId = when (tab) {
            StockTab.Gainers -> "day_gainers"
            StockTab.Losers -> "day_losers"
            StockTab.Actives -> "most_actives"
        }
        val body = get("$SCREENER_URL?scrIds=$scrId&count=25")
        parseQuotes(body)
    }

    private fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15")
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            return response.body?.string().orEmpty()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseQuotes(body: String): List<HotStock> {
        val root = mapAdapter.fromJson(body) ?: return emptyList()
        val finance = root["finance"] as? Map<*, *> ?: return emptyList()
        val result = (finance["result"] as? List<*>)?.firstOrNull() as? Map<*, *> ?: return emptyList()
        val quotes = result["quotes"] as? List<*> ?: return emptyList()

        return quotes.mapNotNull { item ->
            val q = item as? Map<*, *> ?: return@mapNotNull null
            val symbol = q["symbol"]?.toString() ?: return@mapNotNull null
            val price = (q["regularMarketPrice"] as? Number)?.toDouble() ?: return@mapNotNull null
            HotStock(
                symbol = symbol,
                name = STOCK_CN_NAMES[symbol] ?: q["shortName"]?.toString() ?: q["longName"]?.toString() ?: symbol,
                price = price,
                changePercent = (q["regularMarketChangePercent"] as? Number)?.toDouble() ?: 0.0,
                changeAmount = (q["regularMarketChange"] as? Number)?.toDouble() ?: 0.0,
                volume = (q["regularMarketVolume"] as? Number)?.toLong() ?: 0L,
                preMarketPrice = (q["preMarketPrice"] as? Number)?.toDouble(),
                preMarketChangePercent = (q["preMarketChangePercent"] as? Number)?.toDouble(),
            )
        }
    }

    private companion object {
        const val SCREENER_URL =
            "https://query1.finance.yahoo.com/v1/finance/screener/predefined/saved"
        val sharedClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
