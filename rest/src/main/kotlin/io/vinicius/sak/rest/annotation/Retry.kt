package io.vinicius.sak.rest.annotation

/**
 * Retries a failed request on network error ([java.io.IOException]) or a 5xx server response.
 *
 * Place it on a single method, or on the [@Service][Service] interface to set a default for every idempotent method. A
 * method-level annotation overrides the service-level one, and [@NoRetry][NoRetry] disables retries for a method that
 * would otherwise inherit the service default.
 *
 * Only idempotent verbs (GET, PUT, DELETE) may be retried. A service-level `@Retry` is applied to those methods only and
 * silently skipped for POST/PATCH; a **method-level** `@Retry` or `@NoRetry` on a POST/PATCH method is a compile-time
 * error, preventing accidental request duplication.
 *
 * At compile time the `rest-compiler` KSP processor copies the resolved annotation onto the generated `<Name>Retrofit`
 * method, and [io.vinicius.sak.rest.interceptor.RetryInterceptor] reads it at runtime via Retrofit's
 * [retrofit2.Invocation] tag.
 *
 * Usage:
 * ```kotlin
 * @Service
 * @Retry(maxAttempts = 3, delay = 1) // default for all idempotent methods
 * interface UserService {
 *     @GET("users/{id}")
 *     @Retry(maxAttempts = 2) // override for this endpoint
 *     suspend fun getUser(@Path("id") id: Int): User
 *
 *     @GET("health")
 *     @NoRetry // never retried
 *     suspend fun health(): Status
 * }
 * ```
 *
 * @param maxAttempts Total number of tries including the first attempt. Must be >= 1.
 * @param delay How long to wait before each retry, in seconds.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Retry(
    val maxAttempts: Int = 3,
    val delay: Long = 1,
)