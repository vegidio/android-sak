package io.vinicius.sak.rest.interceptor

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes and coalesces token refreshes so that concurrent requests never trigger more than one refresh at a time.
 * Shared by the preemptive path ([HeaderInterceptor]) and the reactive path ([AuthInterceptor]), mirroring the
 * `TokenRefreshCoordinator` actor from the iOS SAK implementation.
 *
 * @param tokenProvider Suspend lambda returning the current verbatim Authorization header value, or `null`.
 * @param tokenRefresher Suspend lambda that performs the refresh and returns the new verbatim header value; throws on
 *   failure.
 */
internal class TokenRefreshCoordinator(
    private val tokenProvider: suspend () -> String?,
    private val tokenRefresher: suspend () -> String,
) {
    private val mutex = Mutex()

    /**
     * Returns a fresh token, refreshing at most once across concurrent callers.
     *
     * [tokenUsedInRequest] is the verbatim value the caller already tried. If, once inside the lock, the current
     * [tokenProvider] value already differs (another caller refreshed first), that newer value is returned without a
     * second network refresh; otherwise [tokenRefresher] is invoked.
     *
     * A refresh failure is wrapped in a [TokenRefreshException] so it travels cleanly out of the OkHttp interceptor
     * chain and is later mapped to [io.vinicius.sak.rest.RestError.TokenRefreshFailed].
     *
     * @throws TokenRefreshException if [tokenRefresher] fails.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun refresh(tokenUsedInRequest: String?): String =
        mutex.withLock {
            val current = tokenProvider()
            if (current != null && current != tokenUsedInRequest) {
                current
            } else {
                try {
                    tokenRefresher()
                } catch (e: Exception) {
                    throw TokenRefreshException(e)
                }
            }
        }
}