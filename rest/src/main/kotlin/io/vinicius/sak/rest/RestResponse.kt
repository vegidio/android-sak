package io.vinicius.sak.rest

/**
 * Generic wrapper around a successful HTTP response.
 *
 * Mirrors iOS SAK's RESTResponse. Decouples the domain layer from Retrofit types —
 * callers never need to depend on [retrofit2.Response] directly.
 *
 * @param T The deserialized body type.
 * @param body The deserialized response body.
 * @param statusCode HTTP status code (e.g. 200, 201).
 * @param headers Response headers as a map of name to value.
 */
data class RestResponse<T>(
    val body: T,
    val statusCode: Int,
    val headers: Map<String, String>,
) {
    companion object {
        /** Converts a [retrofit2.Response] into a [RestResponse]. */
        fun <T : Any> from(response: retrofit2.Response<T>): RestResponse<T> {
            val body = checkNotNull(response.body()) { "Response body is null for ${response.code()}" }
            val headers = buildMap {
                response.headers().forEach { (name, value) -> put(name, value) }
            }
            return RestResponse(body, response.code(), headers)
        }
    }
}
