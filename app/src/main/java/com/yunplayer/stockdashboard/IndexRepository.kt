package com.yunplayer.stockdashboard

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
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
            coroutineScope {
                val ndx = async { fetchChartMeta("^NDX") }
                val nq = async { fetchChartMeta("NQ=F") }
                buildNasdaqQuote(ndx.await(), nq.await())
            }
        }.getOrNull()
    }

    private fun buildNasdaqQuote(ndxMeta: Map<*, *>?, nqMeta: Map<*, *>?): NasdaqQuote? {
        ndxMeta ?: return null
        val price = (ndxMeta["regularMarketPrice"] as? Number)?.toDouble() ?: return null
        val prevClose = (ndxMeta["previousClose"] as? Number)?.toDouble()
            ?: (ndxMeta["chartPreviousClose"] as? Number)?.toDouble()
            ?: return null
        val changePercent = (price - prevClose) / prevClose * 100.0
        val marketState = ndxMeta["marketState"]?.toString()

        val futuresChangePercent = nqMeta?.let { meta ->
            val nqPrice = (meta["regularMarketPrice"] as? Number)?.toDouble()
            val nqPrevClose = (meta["previousClose"] as? Number)?.toDouble()
                ?: (meta["chartPreviousClose"] as? Number)?.toDouble()
            if (nqPrice != null && nqPrevClose != null && nqPrevClose != 0.0) {
                (nqPrice - nqPrevClose) / nqPrevClose * 100.0
            } else null
        }

        return NasdaqQuote(
            price = price,
            changePercent = changePercent,
            futuresChangePercent = futuresChangePercent,
            marketState = marketState,
            updatedAtMillis = System.currentTimeMillis(),
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun fetchChartMeta(symbol: String): Map<*, *>? {
        val encoded = symbol.replace("^", "%5E").replace("=", "%3D")
        val url = "https://query1.finance.yahoo.com/v8/finance/chart/$encoded" +
            "?interval=1d&range=1d&includePrePost=true"
        val body = get(url)
        val root = mapAdapter.fromJson(body) ?: return null
        val chart = root["chart"] as? Map<*, *> ?: return null
        val result = (chart["result"] as? List<*>)?.firstOrNull() as? Map<*, *> ?: return null
        return result["meta"] as? Map<*, *>
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
