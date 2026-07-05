package io.vinicius.sak.rest.interceptor

import java.io.IOException

/**
 * Internal marker exception used to carry a token-refresh failure out of the OkHttp interceptor chain.
 *
 * OkHttp only propagates [IOException]s cleanly through its asynchronous call path (Retrofit's `suspend` adapter uses
 * `enqueue`); throwing any other [Throwable] from an interceptor risks crashing the dispatcher thread. So when a
 * refresh fails, [AuthInterceptor] (and the preemptive path in [io.vinicius.sak.rest.interceptor.HeaderInterceptor])
 * wraps the cause in this [IOException] subtype. [io.vinicius.sak.rest.restCall] then unwraps it into
 * [io.vinicius.sak.rest.RestError.TokenRefreshFailed] before it reaches the caller.
 */
internal class TokenRefreshException(
    cause: Throwable?,
) : IOException(cause)