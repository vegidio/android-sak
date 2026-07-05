package io.vinicius.sak.rest

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Request
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

class RestClientTest {
    private val server = MockWebServer()

    @BeforeEach fun setUp() = server.start()

    @AfterEach fun tearDown() = server.close()

    private fun baseUrl() = server.url("/").toString()

    private fun response(
        code: Int,
        body: String? = null,
    ) = MockResponse
        .Builder()
        .code(code)
        .apply { body?.let { body(it) } }
        .build()

    private fun get(
        client: RestClient,
        path: String,
    ) = client.okHttpClient
        .newCall(Request.Builder().url(server.url(path)).build())
        .execute()

    /** Builds an unsigned JWT whose `exp` claim is [expiresInSeconds] from now. */
    private fun jwt(expiresInSeconds: Long): String {
        val exp = System.currentTimeMillis() / 1_000 + expiresInSeconds
        val header = base64Url("""{"alg":"none"}""")
        val payload = base64Url("""{"exp":$exp}""")
        return "$header.$payload.sig"
    }

    private fun base64Url(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray())

    @Test
    fun `default headers are sent with every request`() {
        server.enqueue(response(200, "\"ok\""))
        val client = RestClient(baseUrl = baseUrl(), defaultHeaders = mapOf("X-App-Version" to "2.0"))
        get(client, "/data")
        assertEquals("2.0", server.takeRequest().headers["X-App-Version"])
    }

    @Test
    fun `token is sent verbatim`() {
        server.enqueue(response(200, "\"ok\""))
        val client = RestClient(baseUrl = baseUrl(), tokenProvider = { "Bearer verbatim-xyz" })
        get(client, "/data")
        assertEquals("Bearer verbatim-xyz", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `end-to-end unauthorized triggers refresh and retry with the new verbatim token`() {
        server.enqueue(response(401))
        server.enqueue(response(200, "\"ok\""))

        var storedToken = "Bearer old-token"
        val client =
            RestClient(
                baseUrl = baseUrl(),
                tokenProvider = { storedToken },
                tokenRefresher = {
                    storedToken = "Bearer refreshed-token"
                    storedToken
                },
            )

        val result = get(client, "/protected")

        assertEquals(200, result.code)
        assertEquals(2, server.requestCount)
        assertEquals("Bearer old-token", server.takeRequest().headers["Authorization"])
        assertEquals("Bearer refreshed-token", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `preemptive refresh replaces an about-to-expire token before sending`() {
        server.enqueue(response(200, "\"ok\""))

        val refreshCount = AtomicInteger(0)
        var storedToken = "Bearer ${jwt(expiresInSeconds = 10)}" // within the 60s window
        val client =
            RestClient(
                baseUrl = baseUrl(),
                tokenProvider = { storedToken },
                tokenRefresher = {
                    refreshCount.incrementAndGet()
                    storedToken = "Bearer refreshed"
                    storedToken
                },
                preemptiveRefresh = 60.seconds,
            )

        get(client, "/data")

        assertEquals(1, refreshCount.get())
        assertEquals("Bearer refreshed", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `preemptive refresh disabled with null does not refresh`() {
        server.enqueue(response(200, "\"ok\""))

        val refreshCount = AtomicInteger(0)
        val token = "Bearer ${jwt(expiresInSeconds = 10)}"
        val client =
            RestClient(
                baseUrl = baseUrl(),
                tokenProvider = { token },
                tokenRefresher = {
                    refreshCount.incrementAndGet()
                    "Bearer refreshed"
                },
                preemptiveRefresh = null,
            )

        get(client, "/data")

        assertEquals(0, refreshCount.get())
        assertEquals(token, server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `two clients keep independent header configuration`() {
        server.enqueue(response(200, "\"ok\""))
        server.enqueue(response(200, "\"ok\""))

        val clientA = RestClient(baseUrl = baseUrl(), defaultHeaders = mapOf("X-Client" to "A"))
        val clientB = RestClient(baseUrl = baseUrl(), defaultHeaders = mapOf("X-Client" to "B"))

        get(clientA, "/data")
        get(clientB, "/data")

        assertEquals("A", server.takeRequest().headers["X-Client"])
        assertEquals("B", server.takeRequest().headers["X-Client"])
    }

    @Test
    fun `malformed baseUrl throws InvalidUrl`() {
        val client = RestClient(baseUrl = "not a url")
        assertThrows(RestError.InvalidUrl::class.java) { client.retrofit }
    }

    @Test
    fun `baseUrl not ending in slash throws InvalidUrl`() {
        val client = RestClient(baseUrl = "https://api.example.com/v1")
        assertThrows(RestError.InvalidUrl::class.java) { client.retrofit }
    }

    @Test
    fun `well-formed baseUrl builds successfully`() {
        val client = RestClient(baseUrl = "https://api.example.com/")
        assertNotNull(client.retrofit)
    }
}