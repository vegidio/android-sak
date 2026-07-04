package io.vinicius.sak.rest.cache

import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ResponseCacheTest {
    @Test
    fun `get on empty cache returns null`() =
        runTest {
            val cache = ResponseCache(maxEntries = 10)
            assertNull(cache.get("https://example.com/api"))
        }

    @Test
    fun `put then get within TTL returns stored value`() =
        runTest {
            val cache = ResponseCache(maxEntries = 10)
            cache.put("key1", """{"id":1}""", 60.seconds)
            assertEquals("""{"id":1}""", cache.get("key1"))
        }

    @Test
    fun `get after TTL expires returns null`() =
        runTest {
            val cache = ResponseCache(maxEntries = 10)
            cache.put("key1", """{"id":1}""", 1.milliseconds) // 1ms TTL
            Thread.sleep(10) // wait for expiry
            assertNull(cache.get("key1"))
        }

    @Test
    fun `inserting beyond maxEntries evicts oldest entry`() =
        runTest {
            val cache = ResponseCache(maxEntries = 2)
            cache.put("key1", "value1", 60.seconds)
            cache.put("key2", "value2", 60.seconds)
            cache.put("key3", "value3", 60.seconds) // should evict key1
            assertNull(cache.get("key1"))
            assertEquals("value2", cache.get("key2"))
            assertEquals("value3", cache.get("key3"))
        }

    @Test
    fun `clear removes all entries`() =
        runTest {
            val cache = ResponseCache(maxEntries = 10)
            cache.put("key1", "value1", 60.seconds)
            cache.put("key2", "value2", 60.seconds)
            cache.clear()
            assertNull(cache.get("key1"))
            assertNull(cache.get("key2"))
        }

    @Test
    fun `put overwrites existing entry for same key`() =
        runTest {
            val cache = ResponseCache(maxEntries = 10)
            cache.put("key1", "original", 60.seconds)
            cache.put("key1", "updated", 60.seconds)
            assertEquals("updated", cache.get("key1"))
        }

    @Test
    fun `per-entry TTL is independent across keys`() =
        runTest {
            val cache = ResponseCache(maxEntries = 10)
            cache.put("short", "gone", 1.milliseconds)
            cache.put("long", "kept", 60.seconds)
            Thread.sleep(10)
            assertNull(cache.get("short"))
            assertEquals("kept", cache.get("long"))
        }

    @Test
    fun `INFINITE ttl entry never expires`() =
        runTest {
            val cache = ResponseCache(maxEntries = 10)
            cache.put("key1", "forever", Duration.INFINITE)
            Thread.sleep(10)
            assertEquals("forever", cache.get("key1"))
        }

    @Test
    fun `non-positive maxEntries means unlimited (no eviction)`() =
        runTest {
            val cache = ResponseCache(maxEntries = -1)
            repeat(1_000) { i -> cache.put("key$i", "value$i", 60.seconds) }
            assertEquals("value0", cache.get("key0"))
            assertEquals("value999", cache.get("key999"))
        }

    @Test
    fun `concurrent put and get do not produce data races`() =
        runTest {
            val cache = ResponseCache(maxEntries = 100)
            val jobs = (1..50).map { i -> launch { cache.put("key$i", "value$i", 60.seconds) } }
            jobs.joinAll()
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