package io.vinicius.sak.rest

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.http.GET
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

class RestClientTest {

    private val server = MockWebServer()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    private fun baseUrl() = server.url("/").toString()

    private fun response(code: Int, body: String? = null) =
        MockResponse.Builder().code(code).apply { body?.let { body(it) } }.build()

    interface TestApi {
        @GET("data")
        suspend fun getData(): String
    }

    @Test
    fun `createService returns a non-null Retrofit proxy`() {
        val client = RestClient(RestConfiguration(baseUrl = baseUrl()))
        val service = client.createService<TestApi>()
        assertNotNull(service)
        client.close()
    }

    @Test
    fun `default headers are sent with every request`() {
        server.enqueue(response(200, "\"ok\""))
        val client = RestClient(
            RestConfiguration(
                baseUrl = baseUrl(),
                defaultHeaders = mapOf("X-App-Version" to "2.0"),
            )
        )
        client.okHttpClient.newCall(
            okhttp3.Request.Builder().url(server.url("/data")).build()
        ).execute()
        val recorded = server.takeRequest()
        assertEquals("2.0", recorded.headers["X-App-Version"])
        client.close()
    }

    @Test
    fun `refreshToken calls tokenRefresher and updates currentToken`() {
        var storedToken = "old-token"
        val client = RestClient(
            RestConfiguration(
                baseUrl = baseUrl(),
                tokenProvider = { storedToken },
                tokenRefresher = { storedToken = "new-token"; true },
            )
        )
        Thread.sleep(100)
        assertEquals("old-token", client.currentToken)

        val result = kotlinx.coroutines.runBlocking { client.refreshToken() }
        assertTrue(result)
        assertEquals("new-token", client.currentToken)
        client.close()
    }

    @Test
    fun `close cancels preemptive refresh with no further calls`() {
        val refreshCount = AtomicInteger(0)
        val client = RestClient(
            RestConfiguration(
                baseUrl = baseUrl(),
                tokenProvider = { "token" },
                tokenRefresher = { refreshCount.incrementAndGet(); true },
                preemptiveRefresh = 1.seconds,
            )
        )
        client.close()
        Thread.sleep(200)
        assertEquals(0, refreshCount.get())
    }

    @Test
    fun `end-to-end 401 triggers refresh and retry with new token`() {
        server.enqueue(response(401))
        server.enqueue(response(200, "ok"))

        var storedToken = "old-token"
        val client = RestClient(
            RestConfiguration(
                baseUrl = baseUrl(),
                tokenProvider = { storedToken },
                tokenRefresher = { storedToken = "refreshed-token"; true },
            )
        )
        Thread.sleep(100)

        val result = client.okHttpClient.newCall(
            okhttp3.Request.Builder()
                .url(server.url("/protected"))
                .header("Authorization", "Bearer old-token")
                .build()
        ).execute()

        assertEquals(200, result.code)
        assertEquals(2, server.requestCount)
        assertEquals("Bearer old-token", server.takeRequest().headers["Authorization"])
        assertEquals("Bearer refreshed-token", server.takeRequest().headers["Authorization"])
        client.close()
    }

    @Test
    fun `end-to-end cached response served on second GET`() {
        server.enqueue(response(200, """{"id":1}"""))

        val client = RestClient(
            RestConfiguration(
                baseUrl = baseUrl(),
                cachePolicy = CachePolicy(enabled = true, ttl = 60.seconds),
            )
        )

        val request = okhttp3.Request.Builder().url(server.url("/data")).build()
        client.okHttpClient.newCall(request).execute().close()
        val second = client.okHttpClient.newCall(request).execute()

        assertEquals("HIT", second.header("X-Cache"))
        assertEquals(1, server.requestCount)
        client.close()
    }
}
