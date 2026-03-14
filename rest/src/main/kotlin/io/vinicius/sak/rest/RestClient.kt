package io.vinicius.sak.rest

import io.vinicius.sak.rest.cache.ResponseCache
import io.vinicius.sak.rest.interceptor.AuthAuthenticator
import io.vinicius.sak.rest.interceptor.CacheInterceptor
import io.vinicius.sak.rest.interceptor.HeaderInterceptor
import io.vinicius.sak.rest.interceptor.RetryInterceptor
import io.vinicius.sak.rest.util.JwtUtility
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
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

/**
 * Main entry point for the SAK REST library. Owns the [Retrofit] and [OkHttpClient] instances and drives all
 * cross-cutting concerns: default headers, caching, retry, JWT injection, reactive 401 token refresh, and preemptive
 * token refresh.
 *
 * Construct once (application/singleton scope) and share:
 * ```kotlin
 * val client = RestClient(
 *     RestConfiguration(
 *         baseUrl = "https://api.example.com/",
 *         defaultHeaders = mapOf("Accept" to "application/json"),
 *         tokenProvider = { tokenStore.get() },
 *         tokenRefresher = { authService.refresh(); true },
 *     )
 * )
 * val userApi = client.createService<UserApiService>()
 * ```
 *
 * Call [close] when the client is no longer needed (e.g., on logout or in ViewModel.onCleared) to cancel the background
 * refresh coroutine and release OkHttp connections.
 */
class RestClient(private val config: RestConfiguration) : AutoCloseable {

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

    private val responseCache: ResponseCache? =
        if (config.cachePolicy.enabled) {
            ResponseCache(config.cachePolicy.ttl, config.cachePolicy.maxEntries)
        } else null

    val okHttpClient: OkHttpClient by lazy { buildOkHttpClient() }

    val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(config.baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json; charset=UTF8".toMediaType()))
            .build()
    }

    init {
        scope.launch {
            currentToken = config.tokenProvider?.invoke()
            startPreemptiveRefreshIfEnabled()
        }
    }

    // region Public API

    /** Creates and returns a type-safe Retrofit service implementation of [T]. */
    inline fun <reified T : Any> createService(): T = retrofit.create(T::class.java)

    /**
     * Forces an immediate token refresh regardless of expiry. Updates [currentToken] on success.
     *
     * @return true if the refresh succeeded, false otherwise.
     */
    suspend fun refreshToken(): Boolean {
        val success = config.tokenRefresher?.invoke() ?: return false
        if (success) currentToken = config.tokenProvider?.invoke()
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
        OkHttpClient.Builder()
            .apply {
                connectTimeout(config.connectTimeout)
                readTimeout(config.readTimeout)
                writeTimeout(config.readTimeout)

                // 1. Cache interceptor — may short-circuit on HIT before any network call
                responseCache?.let { addInterceptor(CacheInterceptor(it)) }

                // 2. Default headers + Bearer token injection
                addInterceptor(
                    HeaderInterceptor(
                        defaultHeaders = config.defaultHeaders,
                        tokenProvider =
                            config.tokenProvider?.let { provider ->
                                { runBlocking { provider() }.also { token -> currentToken = token } }
                            },
                    )
                )

                // 3. Retry on IOException / 5xx; explicitly skips 401
                addInterceptor(RetryInterceptor(config.retryPolicy))

                // 4. 401 → token refresh → retry (OkHttp Authenticator, separate from interceptors)
                config.tokenRefresher?.let {
                    authenticator(
                        AuthAuthenticator(
                            tokenProvider = { currentToken },
                            tokenRefresher = it,
                            onTokenRefreshed = {
                                config.tokenProvider?.invoke()?.also { token -> currentToken = token }
                            },
                        )
                    )
                }

                // 5. HTTP logging — outermost layer for maximum visibility
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
            }
            .build()

    // endregion

    // region Private — Preemptive JWT refresh

    private fun startPreemptiveRefreshIfEnabled() {
        if (config.preemptiveRefresh <= Duration.ZERO) return
        if (config.tokenProvider == null || config.tokenRefresher == null) return

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
        if (JwtUtility.isExpiringSoon(token, config.preemptiveRefresh)) {
            val success = config.tokenRefresher!!.invoke()
            if (success) {
                currentToken = config.tokenProvider?.invoke()
            }
        }
    }

    // endregion

    private companion object {
        // How often the background loop checks token expiry
        val PREEMPTIVE_POLL_INTERVAL = 30.seconds
    }
}
