package io.vinicius.sak.rest.cache

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration

/**
 * In-memory TTL cache for raw response bodies stored as strings.
 *
 * Thread safety is provided by a [Mutex], mirroring Swift actor semantics from the iOS version. Entries are evicted
 * lazily on read (expired entry is removed and null is returned). When [maxEntries] is exceeded, the
 * least-recently-used entry is dropped (true LRU): the backing [LinkedHashMap] is created in access order, so every
 * [get]/[put] moves the touched entry to the tail and eviction always removes the head (the least recently used).
 *
 * The TTL is supplied per entry at [put] time (from each endpoint's `@Cacheable(ttl)` annotation), so a single shared
 * cache can hold entries with different lifetimes. [kotlin.time.Duration.INFINITE] stores an entry that never expires.
 *
 * @param maxEntries Maximum number of entries before oldest-first eviction. A non-positive value means unlimited.
 */
internal class ResponseCache(
    private val maxEntries: Int,
) {
    private data class Entry(
        val body: String,
        val expiresAt: Long,
    )

    // accessOrder = true → get()/put() move the entry to the tail, so eviction drops the least recently used.
    private val store = LinkedHashMap<String, Entry>(16, 0.75f, true)
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
     * Stores [body] under [key] with the given [ttl]. If [maxEntries] is reached, the oldest entry is evicted first.
     */
    suspend fun put(
        key: String,
        body: String,
        ttl: Duration,
    ): Unit =
        mutex.withLock {
            if (maxEntries > 0 && store.size >= maxEntries) {
                store.iterator().also {
                    it.next()
                    it.remove()
                }
            }

            val expiresAt =
                if (ttl == Duration.INFINITE) Long.MAX_VALUE else System.currentTimeMillis() + ttl.inWholeMilliseconds

            store[key] = Entry(body, expiresAt)
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