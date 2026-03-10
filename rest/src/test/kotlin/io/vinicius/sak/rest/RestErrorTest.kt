package io.vinicius.sak.rest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestErrorTest {

    @Test
    fun `InvalidUrl contains the url in message`() {
        val error = RestError.InvalidUrl("not-a-url")
        assertEquals("Invalid URL: not-a-url", error.message)
    }

    @Test
    fun `Network wraps the original cause`() {
        val cause = RuntimeException("connection refused")
        val error = RestError.Network(cause)
        assertEquals(cause, error.cause)
        assertTrue(error.message!!.contains("connection refused"))
    }

    @Test
    fun `HttpError includes status code and body in message`() {
        val error = RestError.HttpError(404, "Not Found")
        assertEquals("HTTP 404: Not Found", error.message)
    }

    @Test
    fun `HttpError with null body omits body from message`() {
        val error = RestError.HttpError(500, null)
        assertEquals("HTTP 500", error.message)
    }

    @Test
    fun `DecodingError wraps the original cause`() {
        val cause = IllegalArgumentException("unexpected token")
        val error = RestError.DecodingError(cause)
        assertEquals(cause, error.cause)
        assertTrue(error.message!!.contains("unexpected token"))
    }

    @Test
    fun `TokenRefreshFailed has a descriptive message`() {
        assertEquals("JWT token refresh failed", RestError.TokenRefreshFailed.message)
    }

    @Test
    fun `RestError subtypes are sealed class members`() {
        val errors: List<RestError> =
            listOf(
                RestError.InvalidUrl("x"),
                RestError.Network(RuntimeException()),
                RestError.HttpError(400, null),
                RestError.DecodingError(RuntimeException()),
                RestError.TokenRefreshFailed,
            )
        assertEquals(5, errors.size)
    }
}
