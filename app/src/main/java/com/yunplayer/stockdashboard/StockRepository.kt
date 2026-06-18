package com.yunplayer.stockdashboard

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

interface StockDataSource {
    suspend fun fetchDashboard(): Dashboard
    suspend fun fetchDetail(id: Int): FundDetail
}

class StockRepository(
    private val client: OkHttpClient = sharedClient,
    private val baseUrl: String = BuildConfig.BASE_URL,
    private val tokenGenerator: JwtTokenGenerator = JwtTokenGenerator(),
    private val parser: ApiPayloadParser = ApiPayloadParser()
) : StockDataSource {
    override suspend fun fetchDashboard(): Dashboard = withContext(Dispatchers.IO) {
        val body = execute("/api/lkjhgfdsa")
        parser.parseDashboard(parser.unwrapResponse(body))
    }

    override suspend fun fetchDetail(id: Int): FundDetail = withContext(Dispatchers.IO) {
        val url = baseUrl.toHttpUrl()
            .newBuilder()
            .addPathSegments("api/lkjhgfdse")
            .addQueryParameter("id", id.toString())
            .build()
        val body = execute(url.toString())
        parser.parseDetail(parser.unwrapResponse(body))
    }

    private fun execute(pathOrUrl: String): String {
        val url = if (pathOrUrl.startsWith("http")) {
            pathOrUrl
        } else {
            baseUrl.toHttpUrl().newBuilder()
                .addPathSegments(pathOrUrl.trimStart('/'))
                .build()
                .toString()
        }
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Authorization", "Bearer ${tokenGenerator.create()}")
            .header("Cache-Control", "no-store")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error(friendlyHttpMessage(response.code))
            return response.body?.string().orEmpty()
        }
    }

    private fun friendlyHttpMessage(code: Int): String = when (code) {
        401, 403 -> "鉴权失败，请稍后重试"
        in 500..599 -> "服务器繁忙，请稍后重试"
        else -> "请求失败（$code），请稍后重试"
    }

    private companion object {
        val sharedClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}
