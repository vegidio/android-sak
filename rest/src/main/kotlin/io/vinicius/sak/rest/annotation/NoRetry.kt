package io.vinicius.sak.rest.annotation

/**
 * Disables automatic retry for a single method that would otherwise inherit a service-level [@Retry][Retry].
 *
 * Only valid on idempotent methods (GET, PUT, DELETE); applying it to a POST/PATCH method is a compile-time error.
 *
 * @see Retry
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class NoRetry