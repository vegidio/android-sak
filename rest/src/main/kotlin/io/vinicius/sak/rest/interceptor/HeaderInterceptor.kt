package io.vinicius.sak.rest.interceptor

import io.vinicius.sak.rest.annotation.SkipAuth
import io.vinicius.sak.rest.util.JwtUtility
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import retrofit2.Invocation
import kotlin.time.Duration

/**
 * OkHttp application interceptor that adds [defaultHeaders] to every outgoing request and injects the Authorization
 * header for authenticated endpoints.
 *
 * Header injection rules:
 * - Default headers are added only if not already present (per-request headers take precedence).
 * - The Authorization header is injected only when [tokenProvider] is non-null and the endpoint is not annotated with
 *   [@SkipAuth][io.vinicius.sak.rest.annotation.SkipAuth]. The token is written **verbatim** — the caller's closures
 *   own the scheme (e.g. `"Bearer …"`).
 *
 * Preemptive refresh is evaluated **per request** here (no background thread): when [coordinator] and a positive
 * [preemptiveRefresh] window are configured and the current JWT expires within that window, the token is refreshed
 * before the request is sent. This mirrors the iOS `expiry - now < preemptiveRefresh` check.
 *
 * [tokenProvider] is a suspend lambda invoked per request via [runBlocking]; OkHttp interceptors run on OkHttp's own
 * thread pool, never on the Android main thread, so blocking here is safe.
 *
 * @param coordinator Shared refresh coordinator, or `null` when no `tokenRefresher` is configured (disables preemptive
 *   refresh).
 * @param preemptiveRefresh How long before JWT expiry to refresh; `null` or [Duration.ZERO] disables preemptive
 *   refresh.
 */
internal class HeaderInterceptor(
    private val defaultHeaders: Map<String, String>,
    private val tokenProvider: (suspend () -> String?)?,
    private val coordinator: TokenRefreshCoordinator?,
    private val preemptiveRefresh: Duration?,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()

        defaultHeaders.forEach { (name, value) ->
            if (original.header(name) == null) builder.addHeader(name, value)
        }

        if (!original.hasSkipAuth() && tokenProvider != null) {
            val token = resolveToken(tokenProvider)
            if (token != null) builder.header("Authorization", token)
        }

        return chain.proceed(builder.build())
    }

    /**
     * Resolves the current token, refreshing preemptively when it is about to expire. A refresh failure propagates as
     * a [TokenRefreshException] (thrown by the coordinator), which travels cleanly out of the interceptor chain.
     */
    private fun resolveToken(provider: suspend () -> String?): String? =
        runBlocking {
            val token = provider() ?: return@runBlocking null
            if (isExpiringSoon(token)) coordinator!!.refresh(token) else token
        }

    /**
     * True when preemptive refresh is enabled and the token's JWT `exp` claim falls within the [preemptiveRefresh]
     * window. Opaque (non-JWT) tokens have no readable expiry, so they are never refreshed preemptively — the reactive
     * [AuthInterceptor] handles their auth failures instead.
     */
    private fun isExpiringSoon(token: String): Boolean {
        val window = preemptiveRefresh
        if (coordinator == null || window == null || window <= Duration.ZERO) return false
        val expiry = JwtUtility.expiryDate(token) ?: return false
        return expiry.time - System.currentTimeMillis() <= window.inWholeMilliseconds
    }
}

/**
 * Reads Retrofit 3's automatically-attached [Invocation] tag to detect [@SkipAuth]. This works out of the box with
 * Retrofit 3 — no custom CallAdapter is needed.
 */
internal fun Request.hasSkipAuth(): Boolean {
    val invocation = tag(Invocation::class.java) ?: return false
    return invocation.method().isAnnotationPresent(SkipAuth::class.java)
}