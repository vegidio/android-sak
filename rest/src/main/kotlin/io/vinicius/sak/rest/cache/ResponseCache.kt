package io.vinicius.sak.rest.cache

import kotlin.time.Duration
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory TTL cache for raw response bodies stored as strings.
 *
 * Thread safety is provided by a [Mutex], mirroring Swift actor semantics from the iOS version. Entries are evicted
 * lazily on read (expired entry is removed and null is returned). When [maxEntries] is exceeded, the oldest
 * insertion-order entry is dropped (LRU-style), using a [LinkedHashMap] which preserves insertion order.
 *
 * @param ttl Time-to-live for each cached entry.
 * @param maxEntries Maximum number of entries before oldest-first eviction.
 */
internal class ResponseCache(private val ttl: Duration, private val maxEntries: Int) {
    private data class Entry(val body: String, val expiresAt: Long)

    private val store = LinkedHashMap<String, Entry>()
    private val mutex = Mutex()

    /** Returns the cached response body for [key] if present and not expired, or null. */
    suspend fun get(key: String): String? =
        mutex.withLock {
            val entry = store[key] ?: return@withLock null
            if (System.currentTimeMillis() > entry.expiresAt) {
                store.remove(key)
                return@withLock null
            }
            entry.body
        }

    /**
     * Stores [body] under [key] with the configured TTL. If [maxEntries] is reached, the oldest entry is evicted first.
     */
    suspend fun put(key: String, body: String): Unit =
        mutex.withLock {
            if (store.size >= maxEntries) {
                store.iterator().also {
                    it.next()
                    it.remove()
                }
            }
            store[key] = Entry(body, System.currentTimeMillis() + ttl.inWholeMilliseconds)
        }

    /** Removes all entries from the cache. */
    suspend fun clear(): Unit = mutex.withLock { store.clear() }

    companion object {
        /**
         * Builds a cache key from a full URL string (including query parameters). [okhttp3.HttpUrl.toString] already
         * includes sorted, encoded query params.
         */
        fun keyFor(url: String): String = url
    }
}
