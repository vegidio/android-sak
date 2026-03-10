package io.vinicius.sak.rest

/** Typed errors produced by [RestClient] and its interceptors */
sealed class RestError : Exception() {

    /** The base URL or a composed request URL is malformed. */
    data class InvalidUrl(val url: String) : RestError() {
        override val message = "Invalid URL: $url"
    }

    /** A low-level network failure (no connectivity, DNS error, timeout). */
    data class Network(override val cause: Throwable) : RestError() {
        override val message = "Network error: ${cause.message}"
    }

    /** The server returned a non-2xx HTTP status after all retries are exhausted. */
    data class HttpError(val statusCode: Int, val body: String?) : RestError() {
        override val message = "HTTP $statusCode${if (body != null) ": $body" else ""}"
    }

    /** The response body could not be deserialized into the expected type. */
    data class DecodingError(override val cause: Throwable) : RestError() {
        override val message = "Decoding error: ${cause.message}"
    }

    /** Token refresh was attempted but [RestConfiguration.tokenRefresher] returned false. */
    data object TokenRefreshFailed : RestError() {
        override val message = "JWT token refresh failed"
    }
}
