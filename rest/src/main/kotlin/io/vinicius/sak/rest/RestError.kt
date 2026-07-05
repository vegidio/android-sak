package io.vinicius.sak.rest

/** Typed errors produced by [RestClient] and its interceptors */
sealed class RestError : Exception() {
    /**
     * The configured `baseUrl` is malformed or does not end in `/`. Thrown eagerly when the generated `<Name>Client`
     * is constructed (the client builds its Retrofit instance up front), so a bad base URL fails fast as a typed
     * [RestError] rather than a raw `IllegalArgumentException`.
     */
    data class InvalidUrl(
        val url: String,
    ) : RestError() {
        override val message = "Invalid URL: $url"
    }

    /** A low-level network failure (no connectivity, DNS error, timeout). */
    data class Network(
        override val cause: Throwable,
    ) : RestError() {
        override val message = "Network error: ${cause.message}"
    }

    /** The server returned a non-2xx HTTP status after all retries are exhausted. */
    data class HttpError(
        val statusCode: Int,
        val body: String?,
    ) : RestError() {
        override val message = "HTTP $statusCode${if (body != null) ": $body" else ""}"
    }

    /** The response body could not be deserialized into the expected type. */
    data class DecodingError(
        override val cause: Throwable,
    ) : RestError() {
        override val message = "Decoding error: ${cause.message}"
    }

    /** Token refresh was attempted but [RestClient]'s `tokenRefresher` returned false. */
    data object TokenRefreshFailed : RestError() {
        override val message = "JWT token refresh failed"
    }
}