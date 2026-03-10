package io.vinicius.sak.graphql

import io.vinicius.sak.graphql.client.GraphQLClient

/**
 * Main entry point for the SAK GraphQL library.
 *
 * Usage:
 * ```
 * val sak = SakGraphQL.builder()
 *     .serverUrl("https://api.example.com/graphql")
 *     .build()
 *
 * val response = sak.client.apollo.query(MyQuery()).execute()
 * ```
 */
class SakGraphQL private constructor(
    val client: GraphQLClient,
) {
    class Builder {
        private var serverUrl: String = ""

        // TODO: add authToken(token: String): Builder
        // TODO: add header(name: String, value: String): Builder
        // TODO: add httpCacheSize(bytes: Long): Builder

        fun serverUrl(url: String): Builder = apply { this.serverUrl = url }

        fun build(): SakGraphQL {
            require(serverUrl.isNotBlank()) { "serverUrl must not be blank" }
            // TODO: pass options to GraphQLClient
            return SakGraphQL(GraphQLClient(serverUrl))
        }
    }

    companion object {
        fun builder(): Builder = Builder()
    }
}
