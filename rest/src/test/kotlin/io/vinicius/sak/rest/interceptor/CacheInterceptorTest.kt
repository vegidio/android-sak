package io.vinicius.sak.rest.interceptor

import io.vinicius.sak.rest.annotation.Cacheable
import io.vinicius.sak.rest.annotation.NoCache
import io.vinicius.sak.rest.cache.ResponseCache
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Invocation

class CacheInterceptorTest {
    private val server = MockWebServer()

    @BeforeEach fun setUp() = server.start()

    @AfterEach fun tearDown() = server.close()

    /**
     * Endpoints whose cache annotations the interceptor reads via the [Invocation] tag (attached automatically by
     * Retrofit at runtime; built by hand here for isolation).
     */
    private interface Endpoints {
        @Cacheable(ttl = 60)
        fun cached()

        @Cacheable // default ttl → never expires
        fun defaultTtl()

        @Cacheable(ttl = 1)
        fun shortTtl()

        @NoCache
        fun noCache()

        fun plain()
    }

    private fun clientWith(cache: ResponseCache): OkHttpClient =
        OkHttpClient.Builder().addInterceptor(CacheInterceptor(cache)).build()

    private fun get(
        methodName: String = "cached",
        path: String = "/data",
    ): Request = tagged(methodName, Request.Builder().url(server.url(path)))

    private fun post(methodName: String = "cached"): Request =
        tagged(methodName, Request.Builder().url(server.url("/data")).post("{}".toRequestBody()))

    private fun tagged(
        methodName: String,
        builder: Request.Builder,
    ): Request {
        val invocation = Invocation.of(Endpoints::class.java.getMethod(methodName), emptyList<Any>())
        return builder.tag(Invocation::class.java, invocation).build()
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
    fun `GET without @Cacheable is never cached`() {
        val cache = ResponseCache(maxEntries = 10)
        repeat(2) { server.enqueue(response(200, "ok")) }
        val client = clientWith(cache)
        client.newCall(get("plain")).execute().close()
        val second = client.newCall(get("plain")).execute()
        assertNull(second.header("X-Cache"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `@NoCache GET is never cached`() {
        val cache = ResponseCache(maxEntries = 10)
        repeat(2) { server.enqueue(response(200, "ok")) }
        val client = clientWith(cache)
        client.newCall(get("noCache")).execute().close()
        val second = client.newCall(get("noCache")).execute()
        assertNull(second.header("X-Cache"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `first GET is a cache MISS and hits the network`() {
        val cache = ResponseCache(maxEntries = 10)
        server.enqueue(response(200, """{"id":1}"""))
        val response = clientWith(cache).newCall(get()).execute()
        assertEquals(200, response.code)
        assertNull(response.header("X-Cache"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `second GET for same URL is a cache HIT`() {
        val cache = ResponseCache(maxEntries = 10)
        server.enqueue(response(200, """{"id":1}"""))
        val client = clientWith(cache)
        client.newCall(get()).execute().close()
        val second = client.newCall(get()).execute()
        assertEquals(200, second.code)
        assertEquals("HIT", second.header("X-Cache"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `@Cacheable with default ttl caches and never expires`() {
        val cache = ResponseCache(maxEntries = 10)
        server.enqueue(response(200, """{"id":1}"""))
        val client = clientWith(cache)
        client.newCall(get("defaultTtl")).execute().close()
        Thread.sleep(10)
        val second = client.newCall(get("defaultTtl")).execute()
        assertEquals("HIT", second.header("X-Cache"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `POST requests are never cached`() {
        val cache = ResponseCache(maxEntries = 10)
        repeat(2) { server.enqueue(response(200, "ok")) }
        val client = clientWith(cache)
        client.newCall(post()).execute().close()
        val second = client.newCall(post()).execute()
        assertNull(second.header("X-Cache"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `non-2xx responses are not stored in cache`() {
        val cache = ResponseCache(maxEntries = 10)
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
        val cache = ResponseCache(maxEntries = 10)
        server.enqueue(response(200, """{"page":1}"""))
        server.enqueue(response(200, """{"page":2}"""))
        val client = clientWith(cache)
        val req1 = get(path = "/data?page=1")
        val req2 = get(path = "/data?page=2")
        client.newCall(req1).execute().close()
        val second = client.newCall(req2).execute()
        assertNull(second.header("X-Cache"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `expired cache entry results in a cache MISS`() {
        val cache = ResponseCache(maxEntries = 10)
        server.enqueue(response(200, "first"))
        server.enqueue(response(200, "second"))
        val client = clientWith(cache)
        client.newCall(get("shortTtl")).execute().close() // 1-second TTL
        Thread.sleep(1100)
        val second = client.newCall(get("shortTtl")).execute()
        assertNull(second.header("X-Cache"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `response body is readable by downstream after caching`() {
        val cache = ResponseCache(maxEntries = 10)
        server.enqueue(response(200, """{"value":"hello"}"""))
        val response = clientWith(cache).newCall(get()).execute()
        val body = response.body.string()
        assertEquals("""{"value":"hello"}""", body)
    }
}