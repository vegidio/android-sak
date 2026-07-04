package io.vinicius.sak.rest.annotation

/**
 * Disables in-memory caching for a single method that would otherwise inherit a service-level [@Cacheable][Cacheable].
 * The request always hits the network.
 *
 * @see Cacheable
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class NoCache