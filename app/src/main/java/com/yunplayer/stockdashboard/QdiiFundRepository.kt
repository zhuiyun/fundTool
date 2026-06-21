package com.yunplayer.stockdashboard

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class QdiiFundRepository(
    private val client: OkHttpClient = defaultClient,
) {
    private val moshi = Moshi.Builder().build()
    private val mapAdapter = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    )

    // Uses Eastmoney's official real-time QDII estimated-NAV service.
    // Response: jsonpgz({"fundcode":"160213","gszzl":"1.01","gztime":"2026-06-20 03:30:00",...})
    // gszzl = 估算涨跌幅 (estimated change %)
    // gztime = last estimation time
    suspend fun fetchEstimate(fund: QdiiFundInfo): QdiiEstimate = withContext(Dispatchers.IO) {
        runCatching {
            val body = get("https://fundgz.1234567.com.cn/js/${fund.code}.js")
            parseEstimate(body, fund)
        }.getOrElse { e ->
            QdiiEstimate(fund = fund, estimatedChangePercent = null, error = e.message ?: "加载失败")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseEstimate(jsonp: String, fund: QdiiFundInfo): QdiiEstimate {
        // Strip JSONP wrapper: "jsonpgz({...})" or "jsonpgz({...});"
        val json = jsonp.trim()
            .replace(Regex("^jsonpgz\\("), "")
            .trimEnd(')', ';', ' ', '\n', '\r')
        val root = mapAdapter.fromJson(json)
            ?: return QdiiEstimate(fund = fund, estimatedChangePercent = null, error = "解析失败")
        val changeStr = root["gszzl"]?.toString()
            ?: return QdiiEstimate(fund = fund, estimatedChangePercent = null)
        val change = changeStr.toDoubleOrNull()
            ?: return QdiiEstimate(fund = fund, estimatedChangePercent = null)
        val gztime = root["gztime"]?.toString()
        return QdiiEstimate(
            fund = fund,
            estimatedChangePercent = change,
            holdingsDate = gztime,
        )
    }

    private fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15"
            )
            .header("Accept", "*/*")
            .header("Referer", "https://fund.eastmoney.com/")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            return resp.body?.string().orEmpty()
        }
    }

    companion object {
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
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
