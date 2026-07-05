package io.vinicius.sak.rest.interceptor

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp application interceptor that reacts to authentication failures by refreshing the token and retrying the
 * original request once with the updated, verbatim Authorization header.
 *
 * "Unauthorized" is decided by the configurable [isUnauthorized] predicate (default: HTTP 401), so servers that signal
 * auth failure with a different status can be supported. The refresh itself is coalesced across concurrent requests by
 * the shared [TokenRefreshCoordinator].
 *
 * The single retry is inherent: an application interceptor's [intercept] runs once per request, and re-issuing via
 * `chain.proceed` does not re-enter this interceptor — so at most one refresh + one retry happens per call.
 *
 * Installed only when a `tokenRefresher` is configured. It sits outside [RetryInterceptor], so a failure that
 * [RetryInterceptor] returns as-is (any 4xx) still reaches this interceptor for the auth-refresh path.
 *
 * @param isUnauthorized Predicate deciding whether a response represents an auth failure that should trigger a refresh.
 * @param coordinator Shared refresh coordinator; returns the new verbatim header value or throws on failure.
 */
internal class AuthInterceptor(
    private val isUnauthorized: (Response) -> Boolean,
    private val coordinator: TokenRefreshCoordinator,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        // Only react on authenticated, non-@SkipAuth endpoints whose response the predicate flags as unauthorized.
        val authHeader = request.header("Authorization")
        if (request.hasSkipAuth() || authHeader == null || !isUnauthorized(response)) return response

        val newToken = try {
            runBlocking { coordinator.refresh(authHeader) }
        } catch (e: TokenRefreshException) {
            response.close()
            throw e
        }

        // Nothing actually changed — don't retry with the same token; surface the original response.
        if (newToken == authHeader) return response

        response.close()
        val newRequest = request
            .newBuilder()
            .header("Authorization", newToken)
            .build()

        return chain.proceed(newRequest)
    }
}