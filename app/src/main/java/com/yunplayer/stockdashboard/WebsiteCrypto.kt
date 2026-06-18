package com.yunplayer.stockdashboard

import androidx.annotation.VisibleForTesting
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class JwtTokenGenerator(
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000L }
) {
    fun create(): String {
        val now = nowEpochSeconds()
        val header = """{"alg":"HS256","typ":"JWT"}"""
        val payload = """{"iat":$now,"exp":${now + 300},"nbf":${now - 5}}"""
        val signingInput = "${base64Url(header.toByteArray(StandardCharsets.UTF_8))}.${base64Url(payload.toByteArray(StandardCharsets.UTF_8))}"
        val signature = hmacSha256(signingInput.toByteArray(StandardCharsets.UTF_8))
        return "$signingInput.${base64Url(signature)}"
    }

    private fun hmacSha256(input: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(WebsiteDecryptor.keyString.take(32).toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(input)
    }

    private fun base64Url(bytes: ByteArray): String {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}

object WebsiteDecryptor {
    internal const val keyString = "MXKhLXhvct1jzXBqSXItKjAyNA=="

    fun decrypt(base64Data: String): String {
        val encrypted = Base64.getDecoder().decode(base64Data)
        if (encrypted.size <= 8) return ""

        val key = keyString.toByteArray(StandardCharsets.UTF_8)
        val output = ByteArray(encrypted.size - 8)
        for (index in output.indices) {
            val seed = encrypted[index % 8].toInt() and 0xff
            val keyByte = key[(index + seed) % key.size].toInt()
            output[index] = ((encrypted[index + 8].toInt() and 0xff) xor keyByte).toByte()
        }
        return String(output, StandardCharsets.UTF_8)
    }

    @VisibleForTesting
    internal fun keyBytesForTests(): ByteArray = keyString.toByteArray(StandardCharsets.UTF_8)
}
