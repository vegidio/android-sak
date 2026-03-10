package io.vinicius.sak.rest.model

/**
 * Sealed class representing all possible outcomes of an API call.
 *
 * Use this as the return type of repository / use-case functions to provide
 * a type-safe way to handle success, error, and loading states in the UI layer.
 *
 * Example:
 * ```
 * when (val response = repo.getUser(id)) {
 *     is ApiResponse.Success -> showUser(response.data)
 *     is ApiResponse.Error   -> showError(response.message)
 *     is ApiResponse.Loading -> showSpinner()
 * }
 * ```
 */
sealed class ApiResponse<out T> {
    data class Success<T>(val data: T, val statusCode: Int) : ApiResponse<T>()
    data class Error(val message: String, val statusCode: Int) : ApiResponse<Nothing>()
    data object Loading : ApiResponse<Nothing>()
}
