package com.yunplayer.stockdashboard

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class IndexRepository(
    private val client: OkHttpClient = defaultClient,
) {
    private val moshi = Moshi.Builder().build()
    private val mapAdapter = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    )

    suspend fun fetchNasdaq(): NasdaqQuote? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://query1.finance.yahoo.com/v7/finance/quote"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("symbols", "^NDX,NQ=F")
                .addQueryParameter(
                    "fields",
                    "regularMarketPrice,regularMarketChangePercent," +
                        "preMarketChangePercent,postMarketChangePercent,marketState"
                )
                .build()
                .toString()
            val body = get(url)
            parseNasdaq(body)
        }.getOrNull()
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseNasdaq(json: String): NasdaqQuote? {
        val root = mapAdapter.fromJson(json) ?: return null
        val response = root["quoteResponse"] as? Map<*, *> ?: return null
        val result = response["result"] as? List<*> ?: return null

        var ndxPrice: Double? = null
        var ndxChangePercent: Double? = null
        var nqFuturesChangePercent: Double? = null
        var marketState: String? = null

        for (item in result) {
            val q = item as? Map<*, *> ?: continue
            val sym = q["symbol"]?.toString() ?: continue
            val regular = (q["regularMarketChangePercent"] as? Number)?.toDouble()
            val pre = (q["preMarketChangePercent"] as? Number)?.toDouble()
            val post = (q["postMarketChangePercent"] as? Number)?.toDouble()
            val state = q["marketState"]?.toString()

            when (sym) {
                "^NDX" -> {
                    ndxPrice = (q["regularMarketPrice"] as? Number)?.toDouble()
                    ndxChangePercent = regular
                    marketState = state
                }
                "NQ=F" -> {
                    nqFuturesChangePercent = when (state) {
                        "PRE", "PREPRE" -> pre ?: regular
                        "POST" -> post?.let { (regular ?: 0.0) + it } ?: regular
                        else -> regular
                    }
                }
            }
        }

        val price = ndxPrice ?: return null
        val change = ndxChangePercent ?: return null
        return NasdaqQuote(
            price = price,
            changePercent = change,
            futuresChangePercent = nqFuturesChangePercent,
            marketState = marketState,
            updatedAtMillis = System.currentTimeMillis(),
        )
    }

    private fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15"
            )
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            return resp.body?.string().orEmpty()
        }
    }

    companion object {
        private val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
