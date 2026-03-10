package io.vinicius.sak.rest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class RestConfigurationTest {

    @Test
    fun `RetryPolicy has correct defaults`() {
        val policy = RetryPolicy()
        assertEquals(3, policy.maxAttempts)
        assertEquals(1.seconds, policy.delay)
    }

    @Test
    fun `RetryPolicy accepts custom values`() {
        val policy = RetryPolicy(maxAttempts = 5, delay = 2.seconds)
        assertEquals(5, policy.maxAttempts)
        assertEquals(2.seconds, policy.delay)
    }

    @Test
    fun `CachePolicy has correct defaults`() {
        val policy = CachePolicy()
        assertFalse(policy.enabled)
        assertEquals(60.seconds, policy.ttl)
        assertEquals(100, policy.maxEntries)
    }

    @Test
    fun `CachePolicy accepts custom values`() {
        val policy = CachePolicy(enabled = true, ttl = 5.seconds, maxEntries = 50)
        assert(policy.enabled)
        assertEquals(5.seconds, policy.ttl)
        assertEquals(50, policy.maxEntries)
    }

    @Test
    fun `RestConfiguration stores all fields correctly`() {
        val config = RestConfiguration(
            baseUrl = "https://api.example.com/",
            defaultHeaders = mapOf("X-App" to "1.0"),
            retryPolicy = RetryPolicy(maxAttempts = 1),
            cachePolicy = CachePolicy(enabled = true),
            preemptiveRefresh = 120.seconds,
            connectTimeout = 10.seconds,
            readTimeout = 20.seconds,
        )
        assertEquals("https://api.example.com/", config.baseUrl)
        assertEquals(mapOf("X-App" to "1.0"), config.defaultHeaders)
        assertEquals(1, config.retryPolicy.maxAttempts)
        assert(config.cachePolicy.enabled)
        assertEquals(120.seconds, config.preemptiveRefresh)
        assertEquals(10.seconds, config.connectTimeout)
        assertEquals(20.seconds, config.readTimeout)
    }

    @Test
    fun `RestConfiguration tokenProvider and tokenRefresher default to null`() {
        val config = RestConfiguration(baseUrl = "https://api.example.com/")
        assertNull(config.tokenProvider)
        assertNull(config.tokenRefresher)
    }
}
