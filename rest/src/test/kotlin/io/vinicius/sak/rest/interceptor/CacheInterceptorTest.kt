package io.vinicius.sak.rest.interceptor

import io.vinicius.sak.rest.cache.ResponseCache
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class CacheInterceptorTest {

    private val server = MockWebServer()

    @Before fun setUp() = server.start()

    @After fun tearDown() = server.close()

    private fun clientWith(cache: ResponseCache): OkHttpClient =
        OkHttpClient.Builder().addInterceptor(CacheInterceptor(cache)).build()

    private fun get(path: String = "/data"): Request = Request.Builder().url(server.url(path)).build()

    private fun post(path: String = "/data"): Request =
        Request.Builder().url(server.url(path)).post("{}".toRequestBody()).build()

    private fun response(code: Int, body: String? = null) =
        MockResponse.Builder().code(code).apply { body?.let { body(it) } }.build()

    @Test
    fun `first GET is a cache MISS and hits the network`() {
        val cache = ResponseCache(ttl = 60.seconds, maxEntries = 10)
        server.enqueue(response(200, """{"id":1}"""))
        val response = clientWith(cache).newCall(get()).execute()
        assertEquals(200, response.code)
        assertNull(response.header("X-Cache"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `second GET for same URL is a cache HIT`() {
        val cache = ResponseCache(ttl = 60.seconds, maxEntries = 10)
        server.enqueue(response(200, """{"id":1}"""))
        val client = clientWith(cache)
        client.newCall(get()).execute().close()
        val second = client.newCall(get()).execute()
        assertEquals(200, second.code)
        assertEquals("HIT", second.header("X-Cache"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `POST requests are never cached`() {
        val cache = ResponseCache(ttl = 60.seconds, maxEntries = 10)
        repeat(2) { server.enqueue(response(200, "ok")) }
        val client = clientWith(cache)
        client.newCall(post()).execute().close()
        val second = client.newCall(post()).execute()
        assertNull(second.header("X-Cache"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `non-2xx responses are not stored in cache`() {
        val cache = ResponseCache(ttl = 60.seconds, maxEntries = 10)
        server.enqueue(response(404))
        server.enqueue(response(200, "ok"))
        val client = clientWith(cache)
        client.newCall(get()).execute().close()
        val second = client.newCall(get()).execute()
        assertNull(second.header("X-Cache"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `different query params produce different cache entries`() {
        val cache = ResponseCache(ttl = 60.seconds, maxEntries = 10)
        server.enqueue(response(200, """{"page":1}"""))
        server.enqueue(response(200, """{"page":2}"""))
        val client = clientWith(cache)
        val req1 = Request.Builder().url(server.url("/data?page=1")).build()
        val req2 = Request.Builder().url(server.url("/data?page=2")).build()
        client.newCall(req1).execute().close()
        val second = client.newCall(req2).execute()
        assertNull(second.header("X-Cache"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `expired cache entry results in a cache MISS`() {
        val cache = ResponseCache(ttl = 1.milliseconds, maxEntries = 10)
        server.enqueue(response(200, "first"))
        server.enqueue(response(200, "second"))
        val client = clientWith(cache)
        client.newCall(get()).execute().close()
        Thread.sleep(10)
        val second = client.newCall(get()).execute()
        assertNull(second.header("X-Cache"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `response body is readable by downstream after caching`() {
        val cache = ResponseCache(ttl = 60.seconds, maxEntries = 10)
        server.enqueue(response(200, """{"value":"hello"}"""))
        val response = clientWith(cache).newCall(get()).execute()
        val body = response.body?.string()
        assertEquals("""{"value":"hello"}""", body)
    }
}
