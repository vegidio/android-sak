# rest

A high-level HTTP client for REST APIs built on [Retrofit](https://github.com/square/retrofit) and [OkHttp](https://github.com/square/okhttp). Handles retry, caching, default headers, and automatic token refresh so you only write request logic.

## Quick start

```kotlin
@Serializable
data class User(val id: Int, val name: String)

interface UserService {
    @GET("users/{id}")
    suspend fun getUser(@Path("id") id: Int): RestResponse<User>
}

val client = RestClient(RestConfiguration(baseUrl = "https://api.example.com/"))
val service = client.createService<UserService>()

val response = service.getUser(1)
println(response.body.name)    // "Alice"
println(response.statusCode)  // 200
```

## Sending requests

Define your API as a Retrofit service interface, then use `createService<T>()` to get a type-safe implementation.

### GET with query parameters

```kotlin
interface UserService {
    @GET("users")
    suspend fun listUsers(
        @Query("page") page: Int,
        @Query("limit") limit: Int,
    ): RestResponse<List<User>>
}

val response = service.listUsers(page = 1, limit = 20)
```

### POST with auto-encoded body

Pass any `@Serializable` value as `@Body` — it is JSON-encoded automatically and `Content-Type: application/json` is set for you:

```kotlin
@Serializable
data class NewUser(val name: String)

interface UserService {
    @POST("users")
    suspend fun createUser(@Body user: NewUser): RestResponse<User>
}

val response = service.createUser(NewUser(name = "Alice"))
```

### Custom per-request headers

Per-request headers always take priority over `defaultHeaders`:

```kotlin
interface UserService {
    @GET("users/{id}")
    suspend fun getUser(
        @Path("id") id: Int,
        @Header("X-Request-ID") requestId: String,
    ): RestResponse<User>
}

val response = service.getUser(id = 1, requestId = UUID.randomUUID().toString())
```

## Error handling

All failures are thrown as subtypes of `RestError`:

| Type | When |
|------|------|
| `InvalidUrl(url)` | The URL string could not be parsed |
| `Network(cause)` | A transport-level failure (no connection, timeout, etc.) |
| `HttpError(statusCode, body)` | Server returned a non-2xx status after all retries |
| `DecodingError(cause)` | Response body could not be decoded into `T` |
| `TokenRefreshFailed` | Token refresh was attempted but `tokenRefresher` returned `false` |

```kotlin
try {
    val response = service.getUser(1)
} catch (e: RestError.HttpError) {
    println("Server error ${e.statusCode}: ${e.body}")
} catch (e: RestError.Network) {
    println("Network failure: ${e.cause}")
} catch (e: RestError.TokenRefreshFailed) {
    println("Session expired — redirect to login")
}
```

## Configuration

All behaviour is controlled through `RestConfiguration`, passed once at init time.

### Default headers

Headers added to every request. A header already present on an individual request takes priority.

```kotlin
val client = RestClient(
    RestConfiguration(
        baseUrl = "https://api.example.com/",
        defaultHeaders = mapOf(
            "Accept" to "application/json",
            "X-API-Version" to "2",
        ),
    )
)
```

### Retry

Failed requests are retried automatically. The default policy retries up to 3 times with a 1-second delay. Retries trigger on network failures and 5xx server errors.

```kotlin
val client = RestClient(
    RestConfiguration(
        baseUrl = "https://api.example.com/",
        retryPolicy = RetryPolicy(maxAttempts = 5, delay = 2.seconds),
    )
)
```

### Caching

GET responses can be cached in memory with a configurable TTL. Once enabled, every GET request is eligible for caching — the second call with the same URL returns the cached response without hitting the network.

```kotlin
val client = RestClient(
    RestConfiguration(
        baseUrl = "https://api.example.com/",
        cachePolicy = CachePolicy(
            enabled = true,
            ttl = 60.seconds,   // cache entries expire after 60 seconds
            maxEntries = 100,   // evict oldest entry when limit is reached
        ),
    )
)

// First call hits the network and stores the response.
// Subsequent calls within the TTL return the cached response.
val response = service.listUsers(page = 1, limit = 20)
```

## Authentication

### Attaching a token to every request

Use `tokenProvider` to supply the current token. Once configured, every request automatically receives an `Authorization: Bearer <token>` header.

```kotlin
val client = RestClient(
    RestConfiguration(
        baseUrl = "https://api.example.com/",
        tokenProvider = { authStore.accessToken },
    )
)
```

### Skipping auth on specific endpoints

Annotate a service method with `@SkipAuth` to opt out of token injection — useful for login or public endpoints:

```kotlin
import io.vinicius.sak.rest.annotation.SkipAuth

interface AuthService {
    @SkipAuth
    @POST("auth/login")
    suspend fun login(@Body credentials: Credentials): RestResponse<LoginResponse>

    @POST("auth/logout")
    suspend fun logout(): RestResponse<Unit>   // token is injected normally
}
```

### Automatic token refresh on 401

Provide `tokenRefresher` to fetch a new token when a 401 is received. The client refreshes the token once and retries the original request automatically. Concurrent requests that all hit 401 share a single refresh call.

```kotlin
val client = RestClient(
    RestConfiguration(
        baseUrl = "https://api.example.com/",
        tokenProvider = { authStore.accessToken },
        tokenRefresher = {
            val newToken = authApi.refresh(authStore.refreshToken)
            authStore.accessToken = newToken
            true   // return false to signal refresh failure
        },
    )
)
```

### Preemptive JWT refresh

Avoid 401 errors entirely by refreshing the token before it expires. Use `preemptiveRefresh` to control how far in advance to refresh (default: 60 seconds). The client polls the token expiry in the background and refreshes automatically when it falls within the threshold.

```kotlin
val client = RestClient(
    RestConfiguration(
        baseUrl = "https://api.example.com/",
        tokenProvider = { authStore.accessToken },
        tokenRefresher = {
            val newToken = authApi.refresh(authStore.refreshToken)
            authStore.accessToken = newToken
            true
        },
        preemptiveRefresh = 60.seconds,   // refresh 60 s before expiry
    )
)
```

> **Note:** Call `client.close()` when the client is no longer needed (e.g. on logout or `ViewModel.onCleared()`) to cancel the background refresh coroutine and release OkHttp resources.

## Key types

| Type | Role |
|------|------|
| `RestClient` | Main entry point — create once, reuse everywhere |
| `RestConfiguration` | All client behaviour in one place |
| `RetryPolicy` | `maxAttempts` + `delay` |
| `CachePolicy` | `enabled`, `ttl`, `maxEntries` |
| `RestResponse<T>` | Decoded response body + `statusCode` + `headers` |
| `RestError` | Sealed error hierarchy thrown on failure |
| `@SkipAuth` | Annotation to skip auth injection on a single endpoint |
