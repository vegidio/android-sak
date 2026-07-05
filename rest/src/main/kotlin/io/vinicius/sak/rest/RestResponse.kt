package io.vinicius.sak.rest

import io.vinicius.sak.rest.interceptor.TokenRefreshException
import kotlinx.serialization.SerializationException
import retrofit2.Response
import java.io.IOException

/**
 * Generic wrapper around an HTTP response.
 *
 * Decouples the domain layer from Retrofit types for the common case, while still exposing the underlying
 * [retrofit2.Response] via [rawResponse] for callers that need it.
 *
 * @param T The deserialized body type.
 * @param body The deserialized response body, or `null` for a no-content response (HTTP 204/205) — in which case
 *   [statusCode], [headers], and [rawResponse] remain available for inspection.
 * @param statusCode HTTP status code (e.g. 200, 201, 204).
 * @param headers Response headers as a map of name to value.
 * @param rawResponse The underlying Retrofit [retrofit2.Response] this wrapper was built from.
 */
data class RestResponse<T>(
    val body: T?,
    val statusCode: Int,
    val headers: Map<String, String>,
    val rawResponse: Response<T>,
) {
    companion object {
        /**
         * Converts a successful [retrofit2.Response] into a [RestResponse].
         *
         * @throws RestError.HttpError if the response is not 2xx. The body is `null` for no-content responses.
         */
        fun <T : Any> from(response: Response<T>): RestResponse<T> {
            if (!response.isSuccessful) {
                throw RestError.HttpError(response.code(), response.errorBody()?.string())
            }

            val headers = buildMap { response.headers().forEach { (name, value) -> put(name, value) } }
            return RestResponse(response.body(), response.code(), headers, response)
        }
    }
}

/**
 * Runs a Retrofit [call] and maps every failure onto the sealed [RestError] hierarchy, so callers of the generated
 * client only ever have to catch one exception type. The generated `<Name>Client` methods delegate here.
 *
 * Mapping:
 * - non-2xx status → [RestError.HttpError]
 * - token refresh failure ([TokenRefreshException]) → [RestError.TokenRefreshFailed]
 * - network failure ([IOException]) → [RestError.Network]
 * - body decoding failure ([SerializationException]) → [RestError.DecodingError]
 */
suspend fun <T : Any> restCall(call: suspend () -> Response<T>): RestResponse<T> =
    try {
        RestResponse.from(call())
    } catch (e: RestError) {
        throw e
    } catch (_: TokenRefreshException) {
        throw RestError.TokenRefreshFailed
    } catch (e: SerializationException) {
        throw RestError.DecodingError(e)
    } catch (e: IOException) {
        throw RestError.Network(e)
    }