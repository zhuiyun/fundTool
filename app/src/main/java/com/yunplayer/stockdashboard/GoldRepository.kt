package com.yunplayer.stockdashboard

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class GoldRepository(
    private val client: OkHttpClient = sharedClient,
    private val endpoint: String = DEFAULT_ENDPOINT,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    moshi: Moshi = Moshi.Builder().build()
) : GoldDataSource {
    private val mapAdapter = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    )

    override suspend fun fetchLatest(): GoldQuote = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(endpoint)
            .header("Accept", "application/json")
            .header("Cache-Control", "no-store")
            .build()
        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("金价请求失败（${response.code}）")
            response.body?.string().orEmpty()
        }
        parseQuote(body)
    }

    internal fun parseQuote(body: String): GoldQuote {
        val root = mapAdapter.fromJson(body).orEmpty()
        val resultData = root["resultData"] as? Map<*, *> ?: error("金价数据暂不可用")
        val data = resultData["datas"] as? Map<*, *> ?: error("金价数据暂不可用")
        val price = data.double("price") ?: error("金价数据暂不可用")
        val yesterday = data.double("yesterdayPrice") ?: price
        val change = price - yesterday
        val apiPercent = data["upAndDownRate"]
            ?.toString()
            ?.replace("%", "")
            ?.trim()
            ?.toDoubleOrNull()
        val percent = apiPercent ?: if (yesterday != 0.0) change / yesterday * 100.0 else 0.0
        return GoldQuote(
            price = price,
            yesterdayPrice = yesterday,
            changeAmount = change,
            changePercent = percent,
            updatedAtMillis = nowMillis()
        )
    }

    private fun Map<*, *>.double(key: String): Double? {
        return (this[key] as? Number)?.toDouble() ?: this[key]?.toString()?.toDoubleOrNull()
    }

    private companion object {
        const val DEFAULT_ENDPOINT =
            "https://ms.jr.jd.com/gw/generic/hj/h5/m/latestPrice?reqData={}"

        val sharedClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }
}
