package io.vinicius.sak.rest

/**
 * Controls how many times a failed request is retried and how long to wait between attempts.
 *
 * @param maxAttempts Total number of tries including the first attempt. Must be >= 1.
 * @param delayMillis Milliseconds to wait before each retry.
 */
data class RetryPolicy(
    val maxAttempts: Int = 3,
    val delayMillis: Long = 1_000L,
)

/**
 * Controls in-memory response caching.
 *
 * @param enabled Whether caching is active. Defaults to false.
 * @param ttlMillis Time-to-live for a cached entry in milliseconds. Defaults to 60 seconds.
 * @param maxEntries Maximum number of entries held in memory before oldest-first eviction.
 */
data class CachePolicy(
    val enabled: Boolean = false,
    val ttlMillis: Long = 60_000L,
    val maxEntries: Int = 100,
)

/**
 * Top-level configuration for [RestClient].
 *
 * @param baseUrl The base URL for all requests. Must end with '/'.
 * @param defaultHeaders Headers added to every outgoing request.
 * @param retryPolicy Retry behaviour on network failure or server error.
 * @param cachePolicy In-memory response cache behaviour.
 * @param tokenProvider Suspend lambda returning the current Bearer token, or null if unauthenticated.
 *                      Called once at startup and after each refresh to update the cached token.
 * @param tokenRefresher Suspend lambda that performs the token refresh and persists the new token.
 *                       Should return true on success, false if the refresh failed.
 * @param preemptiveRefreshSeconds How many seconds before JWT expiry the background refresher fires.
 *                                 Set to 0 to disable preemptive refresh.
 * @param connectTimeoutMillis OkHttp connect timeout in milliseconds.
 * @param readTimeoutMillis OkHttp read/write timeout in milliseconds.
 */
data class RestConfiguration(
    val baseUrl: String,
    val defaultHeaders: Map<String, String> = emptyMap(),
    val retryPolicy: RetryPolicy = RetryPolicy(),
    val cachePolicy: CachePolicy = CachePolicy(),
    val tokenProvider: (suspend () -> String?)? = null,
    val tokenRefresher: (suspend () -> Boolean)? = null,
    val preemptiveRefreshSeconds: Long = 60L,
    val connectTimeoutMillis: Long = 30_000L,
    val readTimeoutMillis: Long = 30_000L,
)
