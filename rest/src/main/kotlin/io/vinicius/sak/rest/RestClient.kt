package io.vinicius.sak.rest

import io.vinicius.sak.rest.cache.ResponseCache
import io.vinicius.sak.rest.interceptor.AuthInterceptor
import io.vinicius.sak.rest.interceptor.CacheInterceptor
import io.vinicius.sak.rest.interceptor.HeaderInterceptor
import io.vinicius.sak.rest.interceptor.HttpStatus
import io.vinicius.sak.rest.interceptor.RetryInterceptor
import io.vinicius.sak.rest.interceptor.TokenRefreshCoordinator
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Internal HTTP engine for the SAK REST library. Owns the [Retrofit] and [OkHttpClient] instances and drives all
 * cross-cutting concerns: default headers, caching, retry, token injection, reactive auth-failure refresh, and
 * per-request preemptive token refresh.
 *
 * This is an implementation detail — application code never touches it. Annotate a service interface with
 * [io.vinicius.sak.rest.annotation.Service]; the `rest-compiler` KSP processor generates a `<Name>Client` (extending
 * the public [ServiceClient] base) that builds and owns one of these for you. Developers construct only the generated
 * client, e.g. `UserServiceClient(baseUrl = "https://api.example.com/")`.
 *
 * Only [baseUrl] is required; every other parameter is optional and falls back to a sensible default.
 *
 * There is no lifecycle to manage: preemptive refresh is evaluated per request (no background coroutine), and OkHttp
 * releases idle connections and threads on its own.
 *
 * Caching and retry are configured per endpoint with the `@Cacheable`/`@NoCache` and `@Retry`/`@NoRetry` annotations on
 * the service interface — not on this engine. See [io.vinicius.sak.rest.annotation.Cacheable] and
 * [io.vinicius.sak.rest.annotation.Retry].
 *
 * @param baseUrl The base URL for all requests. Must end with '/'.
 * @param defaultHeaders Headers added to every outgoing request.
 * @param cacheMaxEntries Maximum number of entries the shared in-memory response cache holds before oldest-first
 *   eviction; -1 (the default) means unlimited. The generated client supplies this from the service-level
 *   `@Cacheable(maxEntries)`.
 * @param tokenProvider Suspend lambda returning the current Authorization header value **verbatim** (scheme included,
 *   e.g. `"Bearer eyJ…"`), or null if unauthenticated. Invoked per request.
 * @param tokenRefresher Suspend lambda that performs the token refresh and returns the new **verbatim** Authorization
 *   header value; it must **throw** on failure. Should also persist the new token so [tokenProvider] returns it next.
 * @param preemptiveRefresh How long before JWT expiry to refresh the token before sending a request. `null` or
 *   [Duration.ZERO] disables preemptive refresh. Requires [tokenRefresher].
 * @param isUnauthorized Predicate deciding whether a response is an auth failure that should trigger a refresh + retry.
 *   Defaults to HTTP 401. Requires [tokenRefresher].
 * @param connectTimeout OkHttp connect timeout.
 * @param readTimeout OkHttp read/write timeout.
 * @param logging Opt-in sink for OkHttp-style request/response logs; null (the default) disables logging entirely.
 *   When set, requests are logged at BODY level — including full bodies and the injected `Authorization` header — so
 *   callers should gate it behind a debug/build-type check to avoid leaking tokens/PII in production.
 */
@Suppress("LongParameterList")
internal class RestClient(
    private val baseUrl: String,
    private val defaultHeaders: Map<String, String> = emptyMap(),
    private val cacheMaxEntries: Int = -1,
    private val tokenProvider: (suspend () -> String?)? = null,
    private val tokenRefresher: (suspend () -> String)? = null,
    private val preemptiveRefresh: Duration? = 60.seconds,
    private val isUnauthorized: (Response) -> Boolean = { it.code == HttpStatus.UNAUTHORIZED },
    private val connectTimeout: Duration = 30.seconds,
    private val readTimeout: Duration = 30.seconds,
    private val logging: ((String) -> Unit)? = null,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    // Always present; the CacheInterceptor only stores/serves responses for @Cacheable endpoints.
    private val responseCache = ResponseCache(cacheMaxEntries)

    // Coalesces token refreshes across the preemptive (HeaderInterceptor) and reactive (AuthInterceptor) paths.
    // Null when no refresher is configured, which also disables both preemptive and reactive refresh.
    private val coordinator: TokenRefreshCoordinator? =
        tokenRefresher?.let { refresher ->
            TokenRefreshCoordinator(
                tokenProvider = tokenProvider ?: { null },
                tokenRefresher = refresher,
            )
        }

    val okHttpClient: OkHttpClient by lazy { buildOkHttpClient() }

    val retrofit: Retrofit by lazy {
        // Validate the base URL narrowly (mirroring Retrofit's own rules) so a bad URL surfaces as a typed
        // RestError.InvalidUrl instead of a raw IllegalArgumentException — without masking unrelated builder errors.
        // pathSegments is never empty (at least [""]); a trailing '/' means the last segment is "".
        val httpUrl = baseUrl.toHttpUrlOrNull()
        if (httpUrl == null || httpUrl.pathSegments.last().isNotEmpty()) {
            throw RestError.InvalidUrl(baseUrl)
        }

        Retrofit
            .Builder()
            .baseUrl(httpUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json; charset=UTF8".toMediaType()))
            .build()
    }

    // region Private — OkHttp construction

    private fun buildOkHttpClient(): OkHttpClient =
        OkHttpClient
            .Builder()
            .apply {
                connectTimeout(connectTimeout)
                readTimeout(readTimeout)
                writeTimeout(readTimeout)

                // 1. Cache interceptor — may short-circuit on HIT before any network call (@Cacheable endpoints only)
                addInterceptor(CacheInterceptor(responseCache))

                // 2. Default headers + verbatim token injection + per-request preemptive refresh
                addInterceptor(
                    HeaderInterceptor(
                        defaultHeaders = defaultHeaders,
                        tokenProvider = tokenProvider,
                        coordinator = coordinator,
                        preemptiveRefresh = preemptiveRefresh,
                    ),
                )

                // 3. Reactive auth-failure refresh + single retry (installed only when a refresher is configured)
                coordinator?.let { addInterceptor(AuthInterceptor(isUnauthorized, it)) }

                // 4. Retry on IOException / 5xx for @Retry endpoints
                addInterceptor(RetryInterceptor())

                // 5. HTTP logging — opt-in; installed as the innermost application interceptor, so it observes the
                //    fully adorned request (after HeaderInterceptor injects the Authorization token)
                logging?.let { sink ->
                    addInterceptor(
                        HttpLoggingInterceptor { message -> sink(message) }
                            .apply { level = HttpLoggingInterceptor.Level.BODY },
                    )
                }
            }.build()

    // endregion
}