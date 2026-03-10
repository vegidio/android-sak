package io.vinicius.sak.rest.interceptor

import io.vinicius.sak.rest.RetryPolicy
import java.io.IOException
import kotlin.time.Duration
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RetryInterceptorTest {

    private val server = MockWebServer()

    @Before fun setUp() = server.start()

    @After fun tearDown() = server.close()

    private fun clientWith(policy: RetryPolicy): OkHttpClient =
        OkHttpClient.Builder().addInterceptor(RetryInterceptor(policy)).build()

    private fun get(): Request = Request.Builder().url(server.url("/test")).build()

    private fun response(code: Int, body: String? = null) =
        MockResponse.Builder().code(code).apply { body?.let { body(it) } }.build()

    @Test
    fun `200 response is returned without retry`() {
        server.enqueue(response(200, "ok"))
        val response = clientWith(RetryPolicy(maxAttempts = 3, delay = Duration.ZERO)).newCall(get()).execute()
        assertEquals(200, response.code)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `401 is returned immediately without retry`() {
        server.enqueue(response(401))
        val response = clientWith(RetryPolicy(maxAttempts = 3, delay = Duration.ZERO)).newCall(get()).execute()
        assertEquals(401, response.code)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `404 is returned immediately without retry`() {
        server.enqueue(response(404))
        val response = clientWith(RetryPolicy(maxAttempts = 3, delay = Duration.ZERO)).newCall(get()).execute()
        assertEquals(404, response.code)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `500 is retried up to maxAttempts`() {
        repeat(3) { server.enqueue(response(500)) }
        val response = clientWith(RetryPolicy(maxAttempts = 3, delay = Duration.ZERO)).newCall(get()).execute()
        assertEquals(500, response.code)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `500 then 200 succeeds after retry`() {
        server.enqueue(response(500))
        server.enqueue(response(500))
        server.enqueue(response(200, "recovered"))
        val response = clientWith(RetryPolicy(maxAttempts = 3, delay = Duration.ZERO)).newCall(get()).execute()
        assertEquals(200, response.code)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `maxAttempts of 1 means no retry on 500`() {
        server.enqueue(response(500))
        val response = clientWith(RetryPolicy(maxAttempts = 1, delay = Duration.ZERO)).newCall(get()).execute()
        assertEquals(500, response.code)
        assertEquals(1, server.requestCount)
    }

    @Test(expected = IOException::class)
    fun `IOException is retried and rethrown after maxAttempts`() {
        server.close()
        val client =
            OkHttpClient.Builder()
                .addInterceptor(RetryInterceptor(RetryPolicy(maxAttempts = 2, delay = Duration.ZERO)))
                .build()
        client.newCall(Request.Builder().url("http://localhost:1").build()).execute()
    }
}
