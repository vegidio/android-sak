package io.vinicius.sak.rest.annotation

/**
 * Enables in-memory response caching for a GET endpoint.
 *
 * Place it on a single method to cache that endpoint, or on the [@Service][Service] interface to cache every GET method
 * by default. A method-level annotation overrides the service-level one, and [@NoCache][NoCache] disables caching for a
 * method that would otherwise inherit the service default.
 *
 * A **method-level** `@Cacheable` may only be applied to a GET method — applying it to another verb is a compile-time
 * error. Likewise, `maxEntries` sizes the shared cache and may only be set on the service-level annotation; setting it
 * on a method is a compile-time error. A service-level `@Cacheable` is applied to GET methods only and silently skipped
 * for other verbs.
 *
 * At compile time the `rest-compiler` KSP processor copies the resolved annotation onto the generated
 * `<Name>Retrofit` method, and [io.vinicius.sak.rest.interceptor.CacheInterceptor] reads it at runtime via Retrofit's
 * [retrofit2.Invocation] tag.
 *
 * Usage:
 * ```kotlin
 * @Service
 * @Cacheable(ttl = 60) // default for all GET methods
 * interface UserService {
 *     @GET("users/{id}")
 *     @Cacheable(ttl = 300) // override: cache this endpoint for 5 minutes
 *     suspend fun getUser(@Path("id") id: Int): User
 *
 *     @GET("health")
 *     @NoCache // always hit the network
 *     suspend fun health(): Status
 * }
 * ```
 *
 * @param ttl Time-to-live for a cached entry, in seconds. Defaults to [NEVER_EXPIRES] (-1): entries never expire.
 * @param maxEntries Maximum number of entries held in memory before oldest-first eviction. Defaults to [UNLIMITED]
 *   (-1): no limit. This sizes the shared cache and may only be set on the service-level annotation; setting it on a
 *   method is a compile-time error.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Cacheable(
    val ttl: Long = NEVER_EXPIRES,
    val maxEntries: Int = UNLIMITED,
) {
    companion object {
        /** Sentinel for [Cacheable.ttl]: the cached entry never expires. */
        const val NEVER_EXPIRES: Long = -1

        /** Sentinel for [Cacheable.maxEntries]: the cache holds an unbounded number of entries. */
        const val UNLIMITED: Int = -1
    }
}