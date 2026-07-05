package io.vinicius.sak.rest.interceptor

import io.mockk.every
import io.mockk.mockk
import io.vinicius.sak.rest.annotation.SkipAuth
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import retrofit2.Invocation
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class AuthInterceptorTest {
    private val server = MockWebServer()

    @BeforeEach fun setUp() = server.start()

    @AfterEach fun tearDown() = server.close()

    private fun clientWith(
        isUnauthorized: (Response) -> Boolean = { it.code == 401 },
        tokenProvider: suspend () -> String? = { "Bearer old" },
        tokenRefresher: suspend () -> String = { "Bearer new" },
    ): OkHttpClient {
        val coordinator = TokenRefreshCoordinator(tokenProvider, tokenRefresher)
        return OkHttpClient
            .Builder()
            .addInterceptor(AuthInterceptor(isUnauthorized, coordinator))
            .build()
    }

    private fun authenticatedGet(): Request =
        Request
            .Builder()
            .url(server.url("/protected"))
            .header("Authorization", "Bearer old")
            .build()

    private fun response(
        code: Int,
        body: String? = null,
    ) = MockResponse
        .Builder()
        .code(code)
        .apply { body?.let { body(it) } }
        .build()

    @Test
    fun `2xx response does not trigger a refresh`() {
        server.enqueue(response(200, "ok"))
        val refreshCount = AtomicInteger(0)
        val client =
            clientWith(
                tokenRefresher = {
                    refreshCount.incrementAndGet()
                    "Bearer new"
                },
            )
        client.newCall(authenticatedGet()).execute()
        assertEquals(0, refreshCount.get())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `unauthorized triggers refresh and retry with the new verbatim token`() {
        server.enqueue(response(401))
        server.enqueue(response(200, "ok"))
        val response = clientWith().newCall(authenticatedGet()).execute()
        assertEquals(200, response.code)
        assertEquals(2, server.requestCount)
        assertEquals("Bearer old", server.takeRequest().headers["Authorization"])
        assertEquals("Bearer new", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `custom predicate treats a non-401 status as unauthorized`() {
        server.enqueue(response(419))
        server.enqueue(response(200, "ok"))
        val response =
            clientWith(isUnauthorized = { it.code == 419 })
                .newCall(authenticatedGet())
                .execute()
        assertEquals(200, response.code)
        assertEquals(2, server.requestCount)
        assertEquals("Bearer old", server.takeRequest().headers["Authorization"])
        assertEquals("Bearer new", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `refresher throwing surfaces as a TokenRefreshException`() {
        server.enqueue(response(401))
        val client = clientWith(tokenRefresher = { throw IllegalStateException("boom") })
        assertThrows<TokenRefreshException> { client.newCall(authenticatedGet()).execute() }
    }

    @Test
    fun `request without Authorization header is not retried`() {
        server.enqueue(response(401))
        val refreshCount = AtomicInteger(0)
        val client =
            clientWith(
                tokenRefresher = {
                    refreshCount.incrementAndGet()
                    "Bearer new"
                },
            )
        val request = Request.Builder().url(server.url("/public")).build()
        val response = client.newCall(request).execute()
        assertEquals(401, response.code)
        assertEquals(0, refreshCount.get())
    }

    @Test
    fun `SkipAuth endpoint is not retried on unauthorized`() {
        server.enqueue(response(401))
        val refreshCount = AtomicInteger(0)
        val method = mockk<Method>(relaxed = true)
        every { method.isAnnotationPresent(SkipAuth::class.java) } returns true
        val invocation = mockk<Invocation>()
        every { invocation.method() } returns method

        val client =
            clientWith(
                tokenRefresher = {
                    refreshCount.incrementAndGet()
                    "Bearer new"
                },
            )
        val request =
            Request
                .Builder()
                .url(server.url("/login"))
                .header("Authorization", "Bearer old")
                .tag(Invocation::class.java, invocation)
                .build()
        val response = client.newCall(request).execute()
        assertEquals(401, response.code)
        assertEquals(0, refreshCount.get())
    }

    @Test
    fun `concurrent unauthorized responses refresh exactly once`() {
        val refreshCount = AtomicInteger(0)
        val currentToken = AtomicReference("Bearer old")

        repeat(2) { server.enqueue(response(401)) }
        repeat(2) { server.enqueue(response(200, "ok")) }

        val client =
            clientWith(
                tokenProvider = { currentToken.get() },
                tokenRefresher = {
                    Thread.sleep(50)
                    refreshCount.incrementAndGet()
                    currentToken.set("Bearer new")
                    "Bearer new"
                },
            )

        val threads = (1..2).map { Thread { client.newCall(authenticatedGet()).execute() } }
        threads.forEach { it.start() }
        threads.forEach { it.join(5_000) }

        assertEquals(1, refreshCount.get())
    }
}