package io.vinicius.sak.rest

import io.vinicius.sak.rest.client.RestClient

/**
 * Main entry point for the SAK REST library.
 *
 * Usage:
 * ```
 * val sak = SakRest.builder()
 *     .baseUrl("https://api.example.com")
 *     .build()
 *
 * val myService = sak.client.createService<MyApiService>()
 * ```
 */
class SakRest private constructor(
    val client: RestClient,
) {
    class Builder {
        private var baseUrl: String = ""

        // TODO: add authToken(token: String): Builder
        // TODO: add connectTimeout(seconds: Long): Builder
        // TODO: add readTimeout(seconds: Long): Builder
        // TODO: add interceptor(interceptor: Interceptor): Builder

        fun baseUrl(url: String): Builder = apply { this.baseUrl = url }

        fun build(): SakRest {
            require(baseUrl.isNotBlank()) { "baseUrl must not be blank" }
            // TODO: pass options to RestClient
            return SakRest(RestClient(baseUrl))
        }
    }

    companion object {
        fun builder(): Builder = Builder()
    }
}
