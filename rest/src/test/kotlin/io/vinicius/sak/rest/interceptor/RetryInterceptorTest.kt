package io.vinicius.sak.rest.interceptor

import io.vinicius.sak.rest.annotation.NoRetry
import io.vinicius.sak.rest.annotation.Retry
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import retrofit2.Invocation
import java.io.IOException

class RetryInterceptorTest {
    private val server = MockWebServer()

    @BeforeEach fun setUp() = server.start()

    @AfterEach fun tearDown() = server.close()

    private val client: OkHttpClient = OkHttpClient.Builder().addInterceptor(RetryInterceptor()).build()

    /**
     * Endpoints whose annotations the interceptor reads via the [Invocation] tag. Retrofit attaches this tag
     * automatically at runtime; here we build it by hand so the interceptor can be unit-tested in isolation.
     * `delay = 0` keeps the tests fast.
     */
    private interface Endpoints {
        @Retry(maxAttempts = 3, delay = 0.0)
        fun retry3()

        @Retry(maxAttempts = 1, delay = 0.0)
        fun retry1()

        @Retry(maxAttempts = 2, delay = 0.5)
        fun retryFractional()

        @NoRetry
        fun noRetry()

        fun plain()
    }

    private fun request(
        methodName: String,
        url: String = server.url("/test").toString(),
    ): Request {
        val invocation = Invocation.of(Endpoints::class.java.getMethod(methodName), emptyList<Any>())
        return Request
            .Builder()
            .url(url)
            .tag(Invocation::class.java, invocation)
            .build()
    }

    private fun response(
        code: Int,
        body: String? = null,
    ) = MockResponse
        .Builder()
        .code(code)
        .apply { body?.let { body(it) } }
        .build()

    @Test
    fun `request without @Retry is attempted once`() {
        server.enqueue(response(500))
        val response = client.newCall(request("plain")).execute()
        assertEquals(500, response.code)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `@NoRetry is attempted once`() {
        server.enqueue(response(500))
        val response = client.newCall(request("noRetry")).execute()
        assertEquals(500, response.code)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `200 response is returned without retry`() {
        server.enqueue(response(200, "ok"))
        val response = client.newCall(request("retry3")).execute()
        assertEquals(200, response.code)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `401 is returned immediately without retry`() {
        server.enqueue(response(401))
        val response = client.newCall(request("retry3")).execute()
        assertEquals(401, response.code)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `404 is returned immediately without retry`() {
        server.enqueue(response(404))
        val response = client.newCall(request("retry3")).execute()
        assertEquals(404, response.code)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `500 is retried up to maxAttempts`() {
        repeat(3) { server.enqueue(response(500)) }
        val response = client.newCall(request("retry3")).execute()
        assertEquals(500, response.code)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `500 then 200 succeeds after retry`() {
        server.enqueue(response(500))
        server.enqueue(response(500))
        server.enqueue(response(200, "recovered"))
        val response = client.newCall(request("retry3")).execute()
        assertEquals(200, response.code)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `maxAttempts of 1 means no retry on 500`() {
        server.enqueue(response(500))
        val response = client.newCall(request("retry1")).execute()
        assertEquals(500, response.code)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `fractional delay is honored between retries`() {
        server.enqueue(response(500))
        server.enqueue(response(200, "ok"))
        val elapsed =
            kotlin.system.measureTimeMillis {
                client.newCall(request("retryFractional")).execute()
            }
        // One 0.5s delay between the two attempts; allow slack for scheduling jitter.
        assertTrue(elapsed >= 450, "expected a ~0.5s delay, was ${elapsed}ms")
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `IOException is retried and rethrown after maxAttempts`() {
        server.close()
        assertThrows<IOException> {
            client.newCall(request("retry3", url = "http://localhost:1")).execute()
        }
    }
}