package io.vinicius.sak.rest.interceptor

import io.vinicius.sak.rest.annotation.SkipAuth
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import retrofit2.Invocation

/**
 * OkHttp application interceptor that adds [defaultHeaders] to every outgoing request
 * and injects the Authorization Bearer token for authenticated endpoints.
 *
 * Header injection rules:
 * - Default headers are added only if not already present (per-request headers take precedence).
 * - The Authorization header is injected only when [tokenProvider] is non-null and the
 *   endpoint is not annotated with [@SkipAuth][io.vinicius.sak.rest.annotation.SkipAuth].
 *
 * [tokenProvider] is a plain (non-suspend) lambda because OkHttp interceptors are synchronous.
 * [io.vinicius.sak.rest.RestClient] exposes its `@Volatile currentToken` field via this lambda,
 * so no blocking calls are needed here.
 */
internal class HeaderInterceptor(
    private val defaultHeaders: Map<String, String>,
    private val tokenProvider: (() -> String?)?,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()

        defaultHeaders.forEach { (name, value) ->
            if (original.header(name) == null) {
                builder.addHeader(name, value)
            }
        }

        if (!original.hasSkipAuth() && tokenProvider != null) {
            val token = tokenProvider.invoke()
            if (token != null) {
                builder.header("Authorization", "Bearer $token")
            }
        }

        return chain.proceed(builder.build())
    }
}

/**
 * Reads Retrofit 3's automatically-attached [Invocation] tag to detect [@SkipAuth].
 * This works out of the box with Retrofit 3 — no custom CallAdapter is needed.
 */
internal fun Request.hasSkipAuth(): Boolean {
    val invocation = tag(Invocation::class.java) ?: return false
    return invocation.method().isAnnotationPresent(SkipAuth::class.java)
}
