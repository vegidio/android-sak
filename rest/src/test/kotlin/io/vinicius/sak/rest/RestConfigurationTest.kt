package io.vinicius.sak.rest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class RestConfigurationTest {

    @Test
    fun `RetryPolicy has correct defaults`() {
        val policy = RetryPolicy()
        assertEquals(3, policy.maxAttempts)
        assertEquals(1_000L, policy.delayMillis)
    }

    @Test
    fun `RetryPolicy accepts custom values`() {
        val policy = RetryPolicy(maxAttempts = 5, delayMillis = 2_000)
        assertEquals(5, policy.maxAttempts)
        assertEquals(2_000L, policy.delayMillis)
    }

    @Test
    fun `CachePolicy has correct defaults`() {
        val policy = CachePolicy()
        assertFalse(policy.enabled)
        assertEquals(60_000L, policy.ttlMillis)
        assertEquals(100, policy.maxEntries)
    }

    @Test
    fun `CachePolicy accepts custom values`() {
        val policy = CachePolicy(enabled = true, ttlMillis = 5_000, maxEntries = 50)
        assert(policy.enabled)
        assertEquals(5_000L, policy.ttlMillis)
        assertEquals(50, policy.maxEntries)
    }

    @Test
    fun `RestConfiguration stores all fields correctly`() {
        val config = RestConfiguration(
            baseUrl = "https://api.example.com/",
            defaultHeaders = mapOf("X-App" to "1.0"),
            retryPolicy = RetryPolicy(maxAttempts = 1),
            cachePolicy = CachePolicy(enabled = true),
            preemptiveRefreshSeconds = 120,
            connectTimeoutMillis = 10_000,
            readTimeoutMillis = 20_000,
        )
        assertEquals("https://api.example.com/", config.baseUrl)
        assertEquals(mapOf("X-App" to "1.0"), config.defaultHeaders)
        assertEquals(1, config.retryPolicy.maxAttempts)
        assert(config.cachePolicy.enabled)
        assertEquals(120L, config.preemptiveRefreshSeconds)
        assertEquals(10_000L, config.connectTimeoutMillis)
        assertEquals(20_000L, config.readTimeoutMillis)
    }

    @Test
    fun `RestConfiguration tokenProvider and tokenRefresher default to null`() {
        val config = RestConfiguration(baseUrl = "https://api.example.com/")
        assertNull(config.tokenProvider)
        assertNull(config.tokenRefresher)
    }
}
