package io.vinicius.sak.rest.interceptor

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * OkHttp [Authenticator] that handles 401 Unauthorized responses by refreshing the JWT token
 * and retrying the original request with the updated token.
 *
 * Thread safety: a [Mutex] ensures only one coroutine performs the token refresh at a time,
 * mirroring the `TokenRefreshCoordinator` actor from the iOS SAK implementation. When multiple
 * requests receive a 401 simultaneously, only the first one calls [tokenRefresher]; the rest
 * detect the already-updated token and skip the refresh.
 *
 * Integration with [RetryInterceptor]: [RetryInterceptor] explicitly returns 401 responses
 * without retrying, so this Authenticator is the sole mechanism that handles 401s.
 *
 * OkHttp only calls [authenticate] for 401 responses (not other status codes). It is set via
 * [okhttp3.OkHttpClient.Builder.authenticator], not via [okhttp3.OkHttpClient.Builder.addInterceptor].
 *
 * @param tokenProvider Non-suspend lambda that returns the current stored token.
 *                      Reads the `@Volatile currentToken` field on [io.vinicius.sak.rest.RestClient].
 * @param tokenRefresher Suspend lambda that performs the actual token refresh (HTTP call).
 *                       Should persist the new token and return true on success.
 * @param onTokenRefreshed Suspend lambda called after a successful refresh to obtain the new token.
 *                         Typically re-invokes [RestConfiguration.tokenProvider] after the refresher
 *                         has persisted the new token.
 */
internal class AuthAuthenticator(
    private val tokenProvider: () -> String?,
    private val tokenRefresher: suspend () -> Boolean,
    private val onTokenRefreshed: suspend () -> String?,
) : Authenticator {

    private val mutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        // Skip @SkipAuth endpoints
        if (response.request.hasSkipAuth()) return null

        // Skip if the request didn't carry an Authorization header
        if (response.request.header("Authorization") == null) return null

        // Prevent infinite loops: if we've already retried once and still got 401, give up
        if (response.responseCount() >= 2) return null

        val newToken: String? = runBlocking {
            mutex.withLock {
                val tokenBeforeLock = tokenProvider()
                val tokenUsedInRequest = response.request
                    .header("Authorization")
                    ?.removePrefix("Bearer ")

                // Another coroutine already refreshed while we waited for the mutex
                if (tokenBeforeLock != null && tokenBeforeLock != tokenUsedInRequest) {
                    return@withLock tokenBeforeLock
                }

                // We are first: perform the refresh
                val success = tokenRefresher()
                if (success) onTokenRefreshed() else null
            }
        }

        return newToken?.let { token ->
            response.request.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }
    }

    /** Counts how many times the response chain has been retried (via prior responses). */
    private fun Response.responseCount(): Int {
        var count = 1
        var prior = priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
