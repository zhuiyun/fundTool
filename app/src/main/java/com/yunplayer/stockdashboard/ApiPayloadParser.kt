package com.yunplayer.stockdashboard

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

class ApiPayloadParser(
    private val moshi: Moshi = Moshi.Builder().build()
) {
    private val listAdapter = moshi.adapter<List<Any?>>(
        Types.newParameterizedType(List::class.java, Any::class.java)
    )
    private val mapAdapter = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    )

    fun unwrapResponse(body: String): String {
        val encryptedWrapper = runCatching { mapAdapter.fromJson(body) }.getOrNull()
        val encrypted = encryptedWrapper?.get("encrypted") as? Boolean
        val data = encryptedWrapper?.get("data") as? String
        return if (encrypted == true && data != null) WebsiteDecryptor.decrypt(data) else body
    }

    fun parseDashboard(plainBody: String): Dashboard {
        val payload = payloadMap(plainBody)
        val indexes = payload.list("indexs").mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            IndexImpact(
                name = map.string("inxnm"),
                changePercent = map.string("rise_fall_per")
            )
        }
        val funds = payload.list("category1mpacts").mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            FundImpact(
                id = map.number("id").toInt(),
                name = map.string("name"),
                estimatedImpact = map.number("estimatedImpact"),
                time = map.string("time")
            )
        }
        return Dashboard(
            indexes = indexes,
            funds = funds,
            timestamp = payload.string("timestamp"),
            description = payload.string("description"),
            hiddenOvernight = payload["hiddenOvernight"] as? Boolean ?: false
        )
    }

    fun parseDetail(plainBody: String): FundDetail {
        val payload = payloadMap(plainBody)
        val holdings = payload.list("stocks").mapNotNull { raw ->
            // 每条持仓为 "名称@@@占比@@@涨跌" 的分隔字符串
            val pieces = raw.toString().split(HOLDING_DELIMITER)
            if (pieces.size < 3 || pieces[0].isBlank()) return@mapNotNull null
            Holding(
                name = pieces[0].trim(),
                weight = parsePercentText(pieces[1]),
                change = parsePercentText(pieces[2])
            )
        }
        return FundDetail(
            id = payload.number("id").toInt(),
            name = payload.string("name"),
            estimatedImpact = payload.number("estimatedImpact"),
            time = payload.string("time"),
            holdings = holdings
        )
    }

    // 接口约定：响应是一个数组，业务负载固定位于第 2 个元素（索引 1）。
    private fun payloadMap(plainBody: String): Map<*, *> {
        val root = listAdapter.fromJson(plainBody).orEmpty()
        return root.getOrNull(PAYLOAD_INDEX) as? Map<*, *> ?: emptyMap<Any, Any>()
    }

    private fun Map<*, *>.list(key: String): List<Any?> {
        return this[key] as? List<Any?> ?: emptyList()
    }

    private fun Map<*, *>.string(key: String): String {
        return this[key]?.toString().orEmpty()
    }

    private fun Map<*, *>.number(key: String): Double {
        return (this[key] as? Number)?.toDouble() ?: this[key]?.toString()?.toDoubleOrNull() ?: 0.0
    }

    private companion object {
        const val PAYLOAD_INDEX = 1
        const val HOLDING_DELIMITER = "@@@"
    }
}
