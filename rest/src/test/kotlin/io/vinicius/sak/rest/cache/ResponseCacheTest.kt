package io.vinicius.sak.rest.cache

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResponseCacheTest {

    @Test
    fun `get on empty cache returns null`() = runTest {
        val cache = ResponseCache(ttlMillis = 60_000, maxEntries = 10)
        assertNull(cache.get("https://example.com/api"))
    }

    @Test
    fun `put then get within TTL returns stored value`() = runTest {
        val cache = ResponseCache(ttlMillis = 60_000, maxEntries = 10)
        cache.put("key1", """{"id":1}""")
        assertEquals("""{"id":1}""", cache.get("key1"))
    }

    @Test
    fun `get after TTL expires returns null`() = runTest {
        val cache = ResponseCache(ttlMillis = 1, maxEntries = 10) // 1ms TTL
        cache.put("key1", """{"id":1}""")
        Thread.sleep(10) // wait for expiry
        assertNull(cache.get("key1"))
    }

    @Test
    fun `inserting beyond maxEntries evicts oldest entry`() = runTest {
        val cache = ResponseCache(ttlMillis = 60_000, maxEntries = 2)
        cache.put("key1", "value1")
        cache.put("key2", "value2")
        cache.put("key3", "value3") // should evict key1
        assertNull(cache.get("key1"))
        assertEquals("value2", cache.get("key2"))
        assertEquals("value3", cache.get("key3"))
    }

    @Test
    fun `clear removes all entries`() = runTest {
        val cache = ResponseCache(ttlMillis = 60_000, maxEntries = 10)
        cache.put("key1", "value1")
        cache.put("key2", "value2")
        cache.clear()
        assertNull(cache.get("key1"))
        assertNull(cache.get("key2"))
    }

    @Test
    fun `put overwrites existing entry for same key`() = runTest {
        val cache = ResponseCache(ttlMillis = 60_000, maxEntries = 10)
        cache.put("key1", "original")
        cache.put("key1", "updated")
        assertEquals("updated", cache.get("key1"))
    }

    @Test
    fun `concurrent put and get do not produce data races`() = runTest {
        val cache = ResponseCache(ttlMillis = 60_000, maxEntries = 100)
        val jobs = (1..50).map { i ->
            launch { cache.put("key$i", "value$i") }
        }
        jobs.forEach { it.join() }
        // All 50 entries should be retrievable
        for (i in 1..50) {
            assertEquals("value$i", cache.get("key$i"))
        }
    }

    @Test
    fun `keyFor returns the url as-is`() {
        val url = "https://api.example.com/users?page=1&sort=asc"
        assertEquals(url, ResponseCache.keyFor(url))
    }
}
