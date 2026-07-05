package io.vinicius.sak.rest.interceptor

import io.vinicius.sak.rest.annotation.Retry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

/**
 * OkHttp application interceptor that retries failed requests for endpoints annotated with
 * [@Retry][io.vinicius.sak.rest.annotation.Retry].
 *
 * Retry is opt-in per endpoint: a request without a `@Retry` annotation (or with
 * [@NoRetry][io.vinicius.sak.rest.annotation.NoRetry]) is attempted exactly once. When `@Retry` is present, its
 * `maxAttempts` and `delay` (seconds) drive the retry loop.
 *
 * Retries occur on:
 * - [IOException] — network failure, connection timeout, DNS error.
 * - 5xx server error responses.
 *
 * Retries do NOT occur on:
 * - Any 4xx client error — these are deterministic failures; retrying won't help. An "unauthorized" response is
 *   returned as-is so the outer [AuthInterceptor] can refresh the token and retry once.
 * - 2xx or 3xx — success / redirects.
 *
 * The delay between retries is applied via [kotlinx.coroutines.delay] inside [runBlocking], which is safe here because
 * OkHttp interceptors run on OkHttp's own thread pool, never on the Android main thread.
 */
internal class RetryInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val retry = request.retry()
        val maxAttempts = retry?.maxAttempts ?: 1
        val delayDuration = (retry?.delay ?: 0.0).seconds

        var response: Response? = null
        var lastException: IOException? = null

        for (attempt in 0 until maxAttempts) {
            try {
                response?.close()
                response = chain.proceed(request)

                when (response.code) {
                    // client error (incl. 401) — no retry here; AuthInterceptor handles the auth-refresh path
                    in HttpStatus.CLIENT_ERROR_RANGE -> return response

                    // success or redirect
                    in HttpStatus.SUCCESS_REDIRECT_RANGE -> return response
                    // 5xx: fall through to retry
                }
            } catch (e: IOException) {
                lastException = e
                response = null
            }

            // Wait before next attempt (skip delay on the last attempt)
            if (attempt < maxAttempts - 1) {
                runBlocking { delay(delayDuration) }
            }
        }

        return response ?: throw lastException ?: IOException("Request failed after $maxAttempts attempt(s)")
    }
}

/**
 * Reads Retrofit 3's automatically-attached [Invocation] tag to resolve the effective [@Retry] for this request, or
 * null when the endpoint carries no retry annotation. Mirrors [hasSkipAuth].
 */
internal fun Request.retry(): Retry? = invokedMethod()?.getAnnotation(Retry::class.java)