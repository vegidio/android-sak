package io.vinicius.sak.rest

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

class RestPoliciesTest {
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
}