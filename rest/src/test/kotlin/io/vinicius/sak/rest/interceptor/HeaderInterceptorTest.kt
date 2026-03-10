package io.vinicius.sak.rest.interceptor

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.vinicius.sak.rest.annotation.SkipAuth
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Invocation
import java.lang.reflect.Method

class HeaderInterceptorTest {

    private fun buildInterceptor(
        headers: Map<String, String> = emptyMap(),
        token: String? = null,
    ) = HeaderInterceptor(
        defaultHeaders = headers,
        tokenProvider = if (token != null || true) ({ token }) else null,
    )

    private fun makeChain(request: Request): Interceptor.Chain {
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        val responseSlot = slot<Request>()
        every { chain.proceed(capture(responseSlot)) } answers {
            Response.Builder()
                .request(responseSlot.captured)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("{}".toResponseBody())
                .build()
        }
        return chain
    }

    private fun plainRequest(url: String = "https://api.example.com/data"): Request =
        Request.Builder().url(url).build()

    private fun skipAuthRequest(url: String = "https://api.example.com/login"): Request {
        val method = mockk<Method>(relaxed = true)
        every { method.isAnnotationPresent(SkipAuth::class.java) } returns true
        val invocation = mockk<Invocation>()
        every { invocation.method() } returns method
        return Request.Builder()
            .url(url)
            .tag(Invocation::class.java, invocation)
            .build()
    }

    @Test
    fun `default headers are added to every request`() {
        val interceptor = buildInterceptor(headers = mapOf("X-App-Version" to "1.0"))
        val chain = makeChain(plainRequest())
        val captured = slot<Request>()
        every { chain.proceed(capture(captured)) } returns mockk(relaxed = true)
        interceptor.intercept(chain)
        assertEquals("1.0", captured.captured.header("X-App-Version"))
    }

    @Test
    fun `default header is not overwritten if already present on request`() {
        val interceptor = buildInterceptor(headers = mapOf("Accept" to "application/json"))
        val request = plainRequest().newBuilder()
            .header("Accept", "text/plain")
            .build()
        val captured = slot<Request>()
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(capture(captured)) } returns mockk(relaxed = true)
        interceptor.intercept(chain)
        assertEquals("text/plain", captured.captured.header("Accept"))
    }

    @Test
    fun `Bearer token is injected when tokenProvider returns non-null`() {
        val interceptor = buildInterceptor(token = "my-token")
        val captured = slot<Request>()
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns plainRequest()
        every { chain.proceed(capture(captured)) } returns mockk(relaxed = true)
        interceptor.intercept(chain)
        assertEquals("Bearer my-token", captured.captured.header("Authorization"))
    }

    @Test
    fun `Bearer token is NOT injected when tokenProvider returns null`() {
        val interceptor = buildInterceptor(token = null)
        val captured = slot<Request>()
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns plainRequest()
        every { chain.proceed(capture(captured)) } returns mockk(relaxed = true)
        interceptor.intercept(chain)
        assertNull(captured.captured.header("Authorization"))
    }

    @Test
    fun `Bearer token is NOT injected for SkipAuth endpoint`() {
        val interceptor = buildInterceptor(token = "my-token")
        val captured = slot<Request>()
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns skipAuthRequest()
        every { chain.proceed(capture(captured)) } returns mockk(relaxed = true)
        interceptor.intercept(chain)
        assertNull(captured.captured.header("Authorization"))
    }

    @Test
    fun `no Authorization header when tokenProvider is null`() {
        val interceptor = HeaderInterceptor(
            defaultHeaders = emptyMap(),
            tokenProvider = null,
        )
        val captured = slot<Request>()
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns plainRequest()
        every { chain.proceed(capture(captured)) } returns mockk(relaxed = true)
        interceptor.intercept(chain)
        assertNull(captured.captured.header("Authorization"))
    }
}
