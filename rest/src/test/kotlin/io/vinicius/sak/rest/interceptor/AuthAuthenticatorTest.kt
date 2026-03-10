package io.vinicius.sak.rest.interceptor

import io.mockk.every
import io.mockk.mockk
import io.vinicius.sak.rest.annotation.SkipAuth
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicInteger
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Invocation

class AuthAuthenticatorTest {

    private val server = MockWebServer()

    @Before fun setUp() = server.start()

    @After fun tearDown() = server.close()

    private fun clientWith(
        tokenProvider: () -> String? = { "old-token" },
        tokenRefresher: suspend () -> Boolean = { true },
        onTokenRefreshed: suspend () -> String? = { "new-token" },
    ): OkHttpClient =
        OkHttpClient.Builder()
            .authenticator(
                AuthAuthenticator(
                    tokenProvider = tokenProvider,
                    tokenRefresher = tokenRefresher,
                    onTokenRefreshed = onTokenRefreshed,
                )
            )
            .build()

    private fun authenticatedGet(): Request =
        Request.Builder().url(server.url("/protected")).header("Authorization", "Bearer old-token").build()

    private fun response(code: Int, body: String? = null) =
        MockResponse.Builder().code(code).apply { body?.let { body(it) } }.build()

    @Test
    fun `200 response does not invoke authenticator`() {
        server.enqueue(response(200, "ok"))
        val refreshCount = AtomicInteger(0)
        val client =
            clientWith(
                tokenRefresher = {
                    refreshCount.incrementAndGet()
                    true
                }
            )
        client.newCall(authenticatedGet()).execute()
        assertEquals(0, refreshCount.get())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `401 triggers token refresh and retry with new token`() {
        server.enqueue(response(401))
        server.enqueue(response(200, "ok"))
        val response = clientWith().newCall(authenticatedGet()).execute()
        assertEquals(200, response.code)
        assertEquals(2, server.requestCount)
        assertEquals("Bearer old-token", server.takeRequest().headers["Authorization"])
        assertEquals("Bearer new-token", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `tokenRefresher returning false propagates 401`() {
        server.enqueue(response(401))
        val response = clientWith(tokenRefresher = { false }).newCall(authenticatedGet()).execute()
        assertEquals(401, response.code)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `request without Authorization header is not retried`() {
        server.enqueue(response(401))
        val refreshCount = AtomicInteger(0)
        val client =
            clientWith(
                tokenRefresher = {
                    refreshCount.incrementAndGet()
                    true
                }
            )
        val request = Request.Builder().url(server.url("/public")).build()
        val response = client.newCall(request).execute()
        assertEquals(401, response.code)
        assertEquals(0, refreshCount.get())
    }

    @Test
    fun `SkipAuth endpoint is not retried on 401`() {
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
                    true
                }
            )
        val request =
            Request.Builder()
                .url(server.url("/login"))
                .header("Authorization", "Bearer token")
                .tag(Invocation::class.java, invocation)
                .build()
        client.newCall(request).execute()
        assertEquals(0, refreshCount.get())
    }

    @Test
    fun `concurrent 401s call tokenRefresher exactly once`() {
        val refreshCount = AtomicInteger(0)
        var currentToken = "old-token"

        repeat(2) { server.enqueue(response(401)) }
        repeat(2) { server.enqueue(response(200, "ok")) }

        val client =
            OkHttpClient.Builder()
                .authenticator(
                    AuthAuthenticator(
                        tokenProvider = { currentToken },
                        tokenRefresher = {
                            Thread.sleep(50)
                            refreshCount.incrementAndGet()
                            currentToken = "new-token"
                            true
                        },
                        onTokenRefreshed = { currentToken },
                    )
                )
                .build()

        val threads = (1..2).map { Thread { client.newCall(authenticatedGet()).execute() } }
        threads.forEach { it.start() }
        threads.forEach { it.join(5_000) }

        assertEquals(1, refreshCount.get())
    }
}
