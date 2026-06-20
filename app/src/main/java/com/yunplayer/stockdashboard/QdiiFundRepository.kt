package com.yunplayer.stockdashboard

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class HoldingsResult(
    val holdings: List<FundHolding>,
    val date: String?,
    val error: String?,
)

// Per-stock effective change given market state.
// marketState: "PRE" | "REGULAR" | "POST" | "CLOSED" | "PREPRE" | null
data class StockQuote(
    val symbol: String,
    val effectiveChangePercent: Double,
    val marketState: String?,
)

class QdiiFundRepository(
    private val client: OkHttpClient = defaultClient,
) {
    private val moshi = Moshi.Builder().build()
    private val mapAdapter = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    )

    suspend fun fetchHoldings(code: String): HoldingsResult = withContext(Dispatchers.IO) {
        runCatching {
            val url = EASTMONEY_DATACENTER_BASE.toHttpUrl().newBuilder()
                .addQueryParameter("reportName", "RPT_F10_FUND_CCBDTA")
                .addQueryParameter("columns", "SECURITY_CODE,SECURITY_NAME,DJZBL,PERIOD_DATE")
                .addQueryParameter("filter", "(FUND_CODE=\"$code\")")
                .addQueryParameter("pageSize", "10")
                .addQueryParameter("sortTypes", "-1")
                .addQueryParameter("sortColumns", "DJZBL")
                .addQueryParameter("source", "F10")
                .addQueryParameter("client", "PC")
                .build()
                .toString()
            val body = get(url)
            parseHoldings(body)
        }.getOrElse { e ->
            HoldingsResult(emptyList(), null, e.message ?: "加载失败")
        }
    }

    // Fetches quotes for the given symbols + USDCNY=X for FX adjustment.
    // Returns a map of symbol -> StockQuote with the effective change percent
    // selected based on current US market state:
    //   PRE   -> preMarketChangePercent  (change from prev close, pre-market price)
    //   POST  -> postMarketChangePercent (after-hours change, cumulative)
    //   other -> regularMarketChangePercent
    suspend fun fetchQuotes(symbols: List<String>): Map<String, StockQuote> =
        withContext(Dispatchers.IO) {
            if (symbols.isEmpty()) return@withContext emptyMap()
            runCatching {
                val all = (symbols + "USDCNY=X").distinct()
                val url = "$YAHOO_QUOTE_BASE?symbols=${all.joinToString(",")}" +
                        "&fields=regularMarketChangePercent,preMarketChangePercent," +
                        "postMarketChangePercent,marketState"
                val body = get(url)
                parseQuoteMap(body)
            }.getOrElse { emptyMap() }
        }

    // Weighted estimate in CNY terms:
    //   estimated_change = Σ(weight_i/100 × stock_change_i) + usdcny_change
    // The USD/CNY term accounts for exchange-rate drift since QDII NAVs are
    // denominated in CNY but hold USD assets.
    fun estimateChange(holdings: List<FundHolding>, quotes: Map<String, StockQuote>): Double? {
        if (holdings.isEmpty()) return null
        var sum = 0.0
        var covered = 0.0
        for (h in holdings) {
            val q = quotes[h.symbol] ?: continue
            sum += h.weight / 100.0 * q.effectiveChangePercent
            covered += h.weight
        }
        if (covered <= 0) return null
        // Add FX adjustment for USD/CNY
        val fx = quotes["USDCNY=X"]?.effectiveChangePercent ?: 0.0
        return sum + fx
    }

    // ── Parsing ───────────────────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun parseHoldings(json: String): HoldingsResult {
        val root = mapAdapter.fromJson(json)
            ?: return HoldingsResult(emptyList(), null, "解析失败")
        val result = root["result"] as? Map<*, *>
            ?: return HoldingsResult(emptyList(), null, root["message"]?.toString() ?: "接口错误")
        val datas = result["data"] as? List<*>
            ?: return HoldingsResult(emptyList(), null, null)
        var date: String? = null
        val holdings = datas.mapNotNull { item ->
            val d = item as? Map<*, *> ?: return@mapNotNull null
            val raw = d["SECURITY_CODE"]?.toString() ?: return@mapNotNull null
            val symbol = normalizeSymbol(raw) ?: return@mapNotNull null
            val name = d["SECURITY_NAME"]?.toString() ?: symbol
            val weight = d["DJZBL"]?.toString()?.toDoubleOrNull() ?: return@mapNotNull null
            if (date == null) date = d["PERIOD_DATE"]?.toString()?.take(10)
            FundHolding(symbol = symbol, name = name, weight = weight)
        }
        return HoldingsResult(holdings, date, null)
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseQuoteMap(json: String): Map<String, StockQuote> {
        val root = mapAdapter.fromJson(json) ?: return emptyMap()
        val response = root["quoteResponse"] as? Map<*, *> ?: return emptyMap()
        val result = response["result"] as? List<*> ?: return emptyMap()
        return buildMap {
            for (item in result) {
                val q = item as? Map<*, *> ?: continue
                val symbol = q["symbol"]?.toString() ?: continue
                val regular = (q["regularMarketChangePercent"] as? Number)?.toDouble() ?: continue
                val preMarket = (q["preMarketChangePercent"] as? Number)?.toDouble()
                val postMarket = (q["postMarketChangePercent"] as? Number)?.toDouble()
                val state = q["marketState"]?.toString()
                val effective = when (state) {
                    "PRE", "PREPRE" -> preMarket ?: regular
                    "POST" -> postMarket?.let { regular + it } ?: regular
                    else -> regular
                }
                put(symbol, StockQuote(symbol, effective, state))
            }
        }
    }

    // Normalises Eastmoney stock codes to Yahoo Finance symbols.
    // "106.AAPL" -> "AAPL", "128.00700" -> "00700.HK", "00700" -> "00700.HK"
    private fun normalizeSymbol(raw: String): String? {
        val clean = if (raw.contains('.') && raw.substringBefore('.').all { it.isDigit() }) {
            raw.substringAfter('.')
        } else raw
        if (clean.isBlank()) return null
        return if (clean.all { it.isDigit() }) clean.padStart(5, '0') + ".HK" else clean.uppercase()
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
        private const val EASTMONEY_DATACENTER_BASE =
            "https://datacenter.eastmoney.com/securities/api/data/v1/get"
        private const val YAHOO_QUOTE_BASE =
            "https://query1.finance.yahoo.com/v7/finance/quote"

        val FUNDS = listOf(
            QdiiFundInfo("160213", "华宝纳斯达克精选"),
            QdiiFundInfo("006697", "浦银安盛全球智能科技"),
            QdiiFundInfo("000592", "广发全球精选"),
            QdiiFundInfo("070023", "嘉实全球产业升级"),
            QdiiFundInfo("070042", "嘉实美国成长"),
            QdiiFundInfo("161128", "易方达标普信息科技"),
            QdiiFundInfo("006105", "易方达全球成长精选"),
            QdiiFundInfo("006479", "国富全球科技"),
            QdiiFundInfo("001549", "建信新兴市场混合"),
            QdiiFundInfo("001182", "汇添富全球移动互联"),
            QdiiFundInfo("008114", "华夏全球科技先锋"),
            QdiiFundInfo("002891", "华夏移动互联"),
            QdiiFundInfo("013228", "银华海外数字经济"),
            QdiiFundInfo("014315", "长城全球新能源车"),
            QdiiFundInfo("008763", "华宝海外新能源汽车"),
            QdiiFundInfo("501301", "华宝海外科技"),
            QdiiFundInfo("213020", "华宝致远混合"),
            QdiiFundInfo("004752", "景顺长城纳斯达克科技"),
            QdiiFundInfo("008975", "天弘全球高端制造"),
            QdiiFundInfo("005881", "富国全球科技互联网"),
            QdiiFundInfo("000934", "国富亚洲机会"),
        )

        private val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
