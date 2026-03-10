package io.vinicius.sak.graphql.model

/**
 * Sealed class representing all possible outcomes of a GraphQL operation.
 *
 * Use this as the return type of repository / use-case functions to provide
 * a type-safe way to handle success, errors, and loading states in the UI layer.
 *
 * Example:
 * ```
 * when (val response = repo.getUser(id)) {
 *     is GraphQLResponse.Success -> showUser(response.data)
 *     is GraphQLResponse.Error   -> showErrors(response.errors)
 *     is GraphQLResponse.Loading -> showSpinner()
 * }
 * ```
 */
sealed class GraphQLResponse<out T> {
    data class Success<T>(val data: T) : GraphQLResponse<T>()
    data class Error(val errors: List<String>) : GraphQLResponse<Nothing>()
    data object Loading : GraphQLResponse<Nothing>()
}
