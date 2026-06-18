package com.yunplayer.stockdashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.Base64

class CryptoTest {
    @Test
    fun decryptsPayloadWithEightBytePrefix() {
        val plain = """[{},{"success":2}]"""
        val encrypted = encryptLikeWebsite(plain, byteArrayOf(11, 22, 33, 44, 55, 66, 77, 88))

        assertEquals(plain, WebsiteDecryptor.decrypt(encrypted))
    }

    @Test
    fun createsDeterministicBearerToken() {
        val token = JwtTokenGenerator(nowEpochSeconds = { 1_700_000_000L }).create()
        val parts = token.split(".")

        assertEquals(3, parts.size)
        assertEquals("""{"alg":"HS256","typ":"JWT"}""", decodeUrl(parts[0]))
        assertEquals("""{"iat":1700000000,"exp":1700000300,"nbf":1699999995}""", decodeUrl(parts[1]))
        assertTrue(parts[2].isNotBlank())
    }

    private fun encryptLikeWebsite(plain: String, prefix: ByteArray): String {
        val key = WebsiteDecryptor.keyBytesForTests()
        val input = plain.toByteArray(StandardCharsets.UTF_8)
        val output = ByteArray(prefix.size + input.size)
        prefix.copyInto(output)
        input.forEachIndexed { index, byte ->
            val seed = prefix[index % 8].toInt() and 0xff
            val keyByte = key[(index + seed) % key.size]
            output[index + 8] = (byte.toInt() xor keyByte.toInt()).toByte()
        }
        return Base64.getEncoder().encodeToString(output)
    }

    private fun decodeUrl(value: String): String {
        return String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
    }
}
