package com.yunplayer.stockdashboard

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryTest {
    @Test
    fun fetchDashboardAddsBearerTokenAndParsesPayload() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{},{"success":2,"indexs":[],"timestamp":"now","description":"desc","hiddenOvernight":false,"category1mpacts":[]},[],{}]""")
        )
        server.start()

        try {
            val repository = StockRepository(
                client = OkHttpClient(),
                baseUrl = server.url("/").toString(),
                tokenGenerator = JwtTokenGenerator(nowEpochSeconds = { 1_700_000_000L }),
                parser = ApiPayloadParser()
            )

            val dashboard = repository.fetchDashboard()
            val request = server.takeRequest()

            assertEquals("now", dashboard.timestamp)
            assertEquals("/api/lkjhgfdsa", request.path)
            assertTrue(request.getHeader("Authorization")?.startsWith("Bearer ") == true)
            assertEquals("application/json", request.getHeader("Accept"))
        } finally {
            server.shutdown()
        }
    }
}
