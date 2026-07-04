package io.vinicius.sak.rest

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Controls how many times a failed request is retried and how long to wait between attempts.
 *
 * @param maxAttempts Total number of tries including the first attempt. Must be >= 1.
 * @param delay How long to wait before each retry.
 */
data class RetryPolicy(
    val maxAttempts: Int = 3,
    val delay: Duration = 1.seconds,
)

/**
 * Controls in-memory response caching.
 *
 * @param enabled Whether caching is active. Defaults to false.
 * @param ttl Time-to-live for a cached entry. Defaults to 60 seconds.
 * @param maxEntries Maximum number of entries held in memory before oldest-first eviction.
 */
data class CachePolicy(
    val enabled: Boolean = false,
    val ttl: Duration = 60.seconds,
    val maxEntries: Int = 100,
)