package io.vinicius.sak.rest.interceptor

/** HTTP status codes and ranges shared by the interceptors. */
internal object HttpStatus {
    const val OK = 200
    const val UNAUTHORIZED = 401

    val SUCCESS_REDIRECT_RANGE = 200..399
    val CLIENT_ERROR_RANGE = 400..499
}