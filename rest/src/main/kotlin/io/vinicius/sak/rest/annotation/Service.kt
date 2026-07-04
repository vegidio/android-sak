package io.vinicius.sak.rest.annotation

/**
 * Marks an interface as a REST service definition. At compile time the `rest-compiler` KSP processor reads the
 * annotated interface and generates a standalone `<Name>Client` class that talks to the underlying [Retrofit] engine.
 *
 * The annotated interface is a declaration only (like a Swift protocol) — it is never instantiated at runtime. You
 * declare each method's return type as the deserialized body type; the generated client returns that body wrapped in
 * [io.vinicius.sak.rest.RestResponse], exposing the status code and headers as well.
 *
 * Usage:
 * ```kotlin
 * @Service
 * interface UserService {
 *     @GET("users/{id}")
 *     suspend fun getUser(@Path("id") id: Int): User
 * }
 *
 * val service = UserServiceClient(baseUrl = "https://api.example.com/")
 * val response = service.getUser(1) // RestResponse<User>
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class Service