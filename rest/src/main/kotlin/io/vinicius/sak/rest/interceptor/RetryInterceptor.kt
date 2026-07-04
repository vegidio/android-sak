package io.vinicius.sak.rest.interceptor

import io.vinicius.sak.rest.RetryPolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * OkHttp application interceptor that retries failed requests according to [policy].
 *
 * Retries occur on:
 * - [IOException] — network failure, connection timeout, DNS error.
 * - 5xx server error responses.
 *
 * Retries do NOT occur on:
 * - 401 Unauthorized — handled exclusively by [AuthAuthenticator] (OkHttp's Authenticator).
 * - Any other 4xx client error — these are deterministic failures; retrying won't help.
 * - 2xx or 3xx — success / redirects.
 *
 * This strict separation prevents a 401 from being retried twice (once here, once by the Authenticator). The delay
 * between retries is applied via [kotlinx.coroutines.delay] inside [runBlocking], which is safe here because OkHttp
 * interceptors run on OkHttp's own thread pool, never on the Android main thread.
 */
internal class RetryInterceptor(
    private val policy: RetryPolicy,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var response: Response? = null
        var lastException: IOException? = null

        for (attempt in 0 until policy.maxAttempts) {
            try {
                response?.close()
                response = chain.proceed(request)

                val code = response.code
                when {
                    // AuthAuthenticator handles 401
                    code == HttpStatus.UNAUTHORIZED -> return response

                    // client error, no retry
                    code in HttpStatus.CLIENT_ERROR_RANGE -> return response

                    // success or redirect
                    code in HttpStatus.SUCCESS_REDIRECT_RANGE -> return response
                    // 5xx: fall through to retry
                }
            } catch (e: IOException) {
                lastException = e
                response = null
            }

            // Wait before next attempt (skip delay on the last attempt)
            if (attempt < policy.maxAttempts - 1) {
                runBlocking { delay(policy.delay) }
            }
        }

        return response ?: throw lastException ?: IOException("Request failed after ${policy.maxAttempts} attempt(s)")
    }
}