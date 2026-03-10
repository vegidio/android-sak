package io.vinicius.sak.graphql.client

import com.apollographql.apollo.ApolloClient

/**
 * Builds and holds a configured [ApolloClient] instance.
 *
 * Responsibilities:
 * - Constructs the Apollo client with the server URL
 * - Configures authentication headers
 * - Sets up HTTP and normalized caching policies
 */
class GraphQLClient(private val serverUrl: String) {

    val apollo: ApolloClient by lazy {
        // TODO: build ApolloClient:
        //   ApolloClient.Builder()
        //       .serverUrl(serverUrl)
        //       .addHttpHeader("Authorization", "Bearer $token")
        //       .httpCache(HttpCache(File(cacheDir, "apollo-http-cache"), cacheSize))
        //       .build()

        TODO("implement ApolloClient setup")
    }
}
