package io.vinicius.sak.rest

import io.vinicius.sak.rest.cache.ResponseCache
import io.vinicius.sak.rest.interceptor.AuthAuthenticator
import io.vinicius.sak.rest.interceptor.CacheInterceptor
import io.vinicius.sak.rest.interceptor.HeaderInterceptor
import io.vinicius.sak.rest.interceptor.RetryInterceptor
import io.vinicius.sak.rest.util.JwtUtility
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Main entry point for the SAK REST library. Owns the [Retrofit] and [OkHttpClient] instances and drives all
 * cross-cutting concerns: default headers, caching, retry, JWT injection, reactive 401 token refresh, and preemptive
 * token refresh.
 *
 * Rather than instantiating this class directly, annotate a service interface with
 * [io.vinicius.sak.rest.annotation.Service]; the `rest-compiler` KSP processor generates a `<Name>Client` that owns a
 * [RestClient] for you. The generated client also exposes a secondary constructor taking an existing [RestClient], so
 * several services can share one instance (and its single token-refresh loop):
 * ```kotlin
 * val client = RestClient(
 *     baseUrl = "https://api.example.com/",
 *     defaultHeaders = mapOf("Accept" to "application/json"),
 *     tokenProvider = { tokenStore.get() },
 *     tokenRefresher = { authService.refresh(); true },
 * )
 * val userApi = UserServiceClient(client)
 * ```
 *
 * Only [baseUrl] is required; every other parameter is optional and falls back to a sensible default.
 *
 * Call [close] when the client is no longer needed (e.g., on logout or in ViewModel.onCleared) to cancel the background
 * refresh coroutine and release OkHttp connections.
 *
 * Caching and retry are configured per endpoint with the `@Cacheable`/`@NoCache` and `@Retry`/`@NoRetry` annotations on
 * the service interface — not on this client. See [io.vinicius.sak.rest.annotation.Cacheable] and
 * [io.vinicius.sak.rest.annotation.Retry].
 *
 * @param baseUrl The base URL for all requests. Must end with '/'.
 * @param defaultHeaders Headers added to every outgoing request.
 * @param cacheMaxEntries Maximum number of entries the shared in-memory response cache holds before oldest-first
 *   eviction; -1 (the default) means unlimited. The generated client supplies this from the service-level
 *   `@Cacheable(maxEntries)`.
 * @param tokenProvider Suspend lambda returning the current Bearer token, or null if unauthenticated. Called once at
 *   startup and after each refresh to update the cached token.
 * @param tokenRefresher Suspend lambda that performs the token refresh and persists the new token. Should return true
 *   on success, false if the refresh failed.
 * @param preemptiveRefresh How long before JWT expiry the background refresher fires. Set to [Duration.ZERO] to disable
 *   preemptive refresh.
 * @param connectTimeout OkHttp connect timeout.
 * @param readTimeout OkHttp read/write timeout.
 */
@Suppress("LongParameterList")
class RestClient(
    private val baseUrl: String,
    private val defaultHeaders: Map<String, String> = emptyMap(),
    private val cacheMaxEntries: Int = -1,
    private val tokenProvider: (suspend () -> String?)? = null,
    private val tokenRefresher: (suspend () -> Boolean)? = null,
    private val preemptiveRefresh: Duration = 60.seconds,
    private val connectTimeout: Duration = 30.seconds,
    private val readTimeout: Duration = 30.seconds,
) : AutoCloseable {
    // Current Bearer token — updated by init, AuthAuthenticator, and the preemptive refresher.
    // @Volatile ensures cross-thread visibility without a lock for simple reads.
    @Volatile internal var currentToken: String? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var refreshJob: Job? = null

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    // Always present; the CacheInterceptor only stores/serves responses for @Cacheable endpoints.
    private val responseCache = ResponseCache(cacheMaxEntries)

    val okHttpClient: OkHttpClient by lazy { buildOkHttpClient() }

    val retrofit: Retrofit by lazy {
        Retrofit
            .Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json; charset=UTF8".toMediaType()))
            .build()
    }

    init {
        scope.launch {
            currentToken = tokenProvider?.invoke()
            startPreemptiveRefreshIfEnabled()
        }
    }

    // region Public API

    /**
     * Forces an immediate token refresh regardless of expiry. Updates [currentToken] on success.
     *
     * @return true if the refresh succeeded, false otherwise.
     */
    suspend fun refreshToken(): Boolean {
        val success = tokenRefresher?.invoke() ?: return false
        if (success) currentToken = tokenProvider?.invoke()
        return success
    }

    /**
     * Cancels the preemptive refresh background coroutine and releases OkHttp resources. Safe to call multiple times.
     */
    override fun close() {
        refreshJob?.cancel()
        scope.cancel()
        // Accessing a `by lazy` property initializes it on first access; shutting down
        // an unused OkHttpClient is harmless, so no guard is needed here.
        okHttpClient.dispatcher.executorService.shutdown()
        okHttpClient.connectionPool.evictAll()
    }

    // endregion

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

                // 2. Default headers + Bearer token injection
                addInterceptor(
                    HeaderInterceptor(
                        defaultHeaders = defaultHeaders,
                        tokenProvider =
                            tokenProvider?.let { provider ->
                                { runBlocking { provider() }.also { token -> currentToken = token } }
                            },
                    ),
                )

                // 3. Retry on IOException / 5xx for @Retry endpoints; explicitly skips 401
                addInterceptor(RetryInterceptor())

                // 4. 401 → token refresh → retry (OkHttp Authenticator, separate from interceptors)
                tokenRefresher?.let {
                    authenticator(
                        AuthAuthenticator(
                            tokenProvider = { currentToken },
                            tokenRefresher = it,
                            onTokenRefreshed = {
                                tokenProvider?.invoke()?.also { token -> currentToken = token }
                            },
                        ),
                    )
                }

                // 5. HTTP logging — outermost layer for maximum visibility
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
            }.build()

    // endregion

    // region Private — Preemptive JWT refresh

    private fun startPreemptiveRefreshIfEnabled() {
        if (preemptiveRefresh <= Duration.ZERO) return
        if (tokenProvider == null || tokenRefresher == null) return

        refreshJob =
            scope.launch {
                while (isActive) {
                    delay(PREEMPTIVE_POLL_INTERVAL)
                    checkAndPreemptivelyRefresh()
                }
            }
    }

    private suspend fun checkAndPreemptivelyRefresh() {
        val token = currentToken ?: return
        if (JwtUtility.isExpiringSoon(token, preemptiveRefresh)) {
            val success = tokenRefresher!!.invoke()
            if (success) {
                currentToken = tokenProvider?.invoke()
            }
        }
    }

    // endregion

    private companion object {
        // How often the background loop checks token expiry
        val PREEMPTIVE_POLL_INTERVAL = 30.seconds
    }
}