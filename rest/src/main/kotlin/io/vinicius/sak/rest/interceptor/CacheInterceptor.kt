package io.vinicius.sak.rest.interceptor

import io.vinicius.sak.rest.annotation.Cacheable
import io.vinicius.sak.rest.annotation.NoCache
import io.vinicius.sak.rest.cache.ResponseCache
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Invocation
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * OkHttp application interceptor providing in-memory response caching.
 *
 * Behaviour:
 * - Caching is opt-in per endpoint: a request is cached only when its service method carries
 *   [@Cacheable][io.vinicius.sak.rest.annotation.Cacheable] (and not
 *   [@NoCache][io.vinicius.sak.rest.annotation.NoCache]). Every other request passes straight through.
 * - Only GET requests are cached.
 * - Only 2xx responses are stored, with the TTL taken from the annotation's `ttl` (seconds).
 * - Cache key is the full request URL including query parameters.
 * - On a cache hit, a synthetic [Response] is returned immediately without a network call. The synthetic response
 *   carries an `X-Cache: HIT` header for observability/testing.
 * - [Response.peekBody] is used to read the body without consuming it, so downstream Retrofit converters still receive
 *   a complete response body.
 *
 * This interceptor is added via [okhttp3.OkHttpClient.Builder.addInterceptor] (application layer), so it runs before
 * the network layer and can fully short-circuit it on a cache hit.
 */
internal class CacheInterceptor(
    private val cache: ResponseCache,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        val cacheable = request.cacheable()
        if (cacheable == null || request.method != "GET") return chain.proceed(request)

        val key = ResponseCache.keyFor(request.url.toString())

        val cached = runBlocking { cache.get(key) }
        if (cached != null) {
            return Response
                .Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(HttpStatus.OK)
                .message("OK (cached)")
                .addHeader("X-Cache", "HIT")
                .body(cached.toResponseBody("application/json".toMediaType()))
                .build()
        }

        val response = chain.proceed(request)

        if (response.isSuccessful) {
            val bodyString = response.peekBody(Long.MAX_VALUE).string()
            val ttl = if (cacheable.ttl == Cacheable.NEVER_EXPIRES) Duration.INFINITE else cacheable.ttl.seconds
            runBlocking { cache.put(key, bodyString, ttl) }
        }

        return response
    }
}

/**
 * Reads Retrofit 3's automatically-attached [Invocation] tag to resolve the effective [@Cacheable] for this request.
 * Returns null when the endpoint opts out via [@NoCache][io.vinicius.sak.rest.annotation.NoCache] or carries no cache
 * annotation at all. Mirrors [hasSkipAuth].
 */
internal fun Request.cacheable(): Cacheable? {
    val method = tag(Invocation::class.java)?.method() ?: return null
    if (method.isAnnotationPresent(NoCache::class.java)) return null
    return method.getAnnotation(Cacheable::class.java)
}