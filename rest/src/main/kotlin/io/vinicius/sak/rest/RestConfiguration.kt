package io.vinicius.sak.rest

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Controls how many times a failed request is retried and how long to wait between attempts.
 *
 * @param maxAttempts Total number of tries including the first attempt. Must be >= 1.
 * @param delay How long to wait before each retry.
 */
data class RetryPolicy(val maxAttempts: Int = 3, val delay: Duration = 1.seconds)

/**
 * Controls in-memory response caching.
 *
 * @param enabled Whether caching is active. Defaults to false.
 * @param ttl Time-to-live for a cached entry. Defaults to 60 seconds.
 * @param maxEntries Maximum number of entries held in memory before oldest-first eviction.
 */
data class CachePolicy(val enabled: Boolean = false, val ttl: Duration = 60.seconds, val maxEntries: Int = 100)

/**
 * Top-level configuration for [RestClient].
 *
 * @param baseUrl The base URL for all requests. Must end with '/'.
 * @param defaultHeaders Headers added to every outgoing request.
 * @param retryPolicy Retry behaviour on network failure or server error.
 * @param cachePolicy In-memory response cache behaviour.
 * @param tokenProvider Suspend lambda returning the current Bearer token, or null if unauthenticated. Called once at
 *   startup and after each refresh to update the cached token.
 * @param tokenRefresher Suspend lambda that performs the token refresh and persists the new token. Should return true
 *   on success, false if the refresh failed.
 * @param preemptiveRefresh How long before JWT expiry the background refresher fires. Set to [Duration.ZERO] to disable
 *   preemptive refresh.
 * @param connectTimeout OkHttp connect timeout.
 * @param readTimeout OkHttp read/write timeout.
 */
data class RestConfiguration(
    val baseUrl: String,
    val defaultHeaders: Map<String, String> = emptyMap(),
    val retryPolicy: RetryPolicy = RetryPolicy(),
    val cachePolicy: CachePolicy = CachePolicy(),
    val tokenProvider: (suspend () -> String?)? = null,
    val tokenRefresher: (suspend () -> Boolean)? = null,
    val preemptiveRefresh: Duration = 60.seconds,
    val connectTimeout: Duration = 30.seconds,
    val readTimeout: Duration = 30.seconds,
)
