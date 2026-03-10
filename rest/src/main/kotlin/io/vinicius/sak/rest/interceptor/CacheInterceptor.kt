package io.vinicius.sak.rest.interceptor

import io.vinicius.sak.rest.cache.ResponseCache
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * OkHttp application interceptor providing in-memory response caching.
 *
 * Behaviour:
 * - Only GET requests are cached.
 * - Only 2xx responses are stored.
 * - Cache key is the full request URL including query parameters.
 * - On a cache hit, a synthetic [Response] is returned immediately without a network call.
 *   The synthetic response carries an `X-Cache: HIT` header for observability/testing.
 * - [Response.peekBody] is used to read the body without consuming it, so downstream
 *   Retrofit converters still receive a complete response body.
 *
 * This interceptor is added via [okhttp3.OkHttpClient.Builder.addInterceptor] (application layer),
 * so it runs before the network layer and can fully short-circuit it on a cache hit.
 */
internal class CacheInterceptor(private val cache: ResponseCache) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (request.method != "GET") return chain.proceed(request)

        val key = ResponseCache.keyFor(request.url.toString())

        val cached = runBlocking { cache.get(key) }
        if (cached != null) {
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK (cached)")
                .addHeader("X-Cache", "HIT")
                .body(cached.toResponseBody("application/json".toMediaType()))
                .build()
        }

        val response = chain.proceed(request)

        if (response.isSuccessful) {
            val bodyString = response.peekBody(Long.MAX_VALUE).string()
            runBlocking { cache.put(key, bodyString) }
        }

        return response
    }
}
