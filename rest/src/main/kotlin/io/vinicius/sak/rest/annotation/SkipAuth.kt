package io.vinicius.sak.rest.annotation

/**
 * Marks a Retrofit service method so that [io.vinicius.sak.rest.interceptor.HeaderInterceptor]
 * skips injecting the Authorization header and [io.vinicius.sak.rest.interceptor.AuthAuthenticator]
 * skips token refresh for that endpoint.
 *
 * Retrofit 3 automatically stores a [retrofit2.Invocation] tag on every OkHttp request, which
 * carries the service method's annotations. No custom CallAdapter is needed.
 *
 * Usage:
 * ```kotlin
 * interface AuthService {
 *     @SkipAuth
 *     @POST("auth/token")
 *     suspend fun login(@Body body: LoginRequest): TokenResponse
 * }
 * ```
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class SkipAuth
