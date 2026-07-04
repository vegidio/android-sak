# rest

A high-level HTTP client for REST APIs built on [Retrofit](https://github.com/square/retrofit) and [OkHttp](https://github.com/square/okhttp). Handles retry, caching, default headers, and automatic token refresh so you only write request logic.

You declare your API as an interface annotated with `@Service`; at compile time the `rest-compiler` KSP processor generates a `<Name>Client` for you. You declare each method's **body type** as its return type, and the generated client returns that body wrapped in a `RestResponse<T>` (body + status code + headers).

## Setup

The generated clients are produced by a KSP processor, so a consuming module applies the KSP plugin and adds both artifacts:

```kotlin
plugins {
    id("com.google.devtools.ksp") version "2.3.9"
}

dependencies {
    implementation("io.vinicius.sak:rest:<version>")
    ksp("io.vinicius.sak:rest-compiler:<version>")
}
```

## Quick start

```kotlin
import io.vinicius.sak.rest.annotation.Service

@Serializable
data class User(val id: Int, val name: String)

@Service
interface UserService {
    @GET("users/{id}")
    suspend fun getUser(@Path("id") id: Int): User
}

val service = UserServiceClient(baseUrl = "https://api.example.com/")

val response = service.getUser(1)   // RestResponse<User>
println(response.body.name)         // "Alice"
println(response.statusCode)        // 200
```

Annotating `UserService` with `@Service` generates a `UserServiceClient` class. You declare `getUser` as returning `User`; calling it on the generated client returns `RestResponse<User>`.

## Sending requests

Define your API as an interface annotated with `@Service`, using the standard Retrofit request annotations (`@GET`, `@POST`, `@Path`, `@Query`, `@Body`, `@Header`, …). Every method must be `suspend`.

### GET with query parameters

```kotlin
@Service
interface UserService {
    @GET("users")
    suspend fun listUsers(
        @Query("page") page: Int,
        @Query("limit") limit: Int,
    ): List<User>
}

val response = service.listUsers(page = 1, limit = 20)   // RestResponse<List<User>>
```

### POST with auto-encoded body

Pass any `@Serializable` value as `@Body` — it is JSON-encoded automatically and `Content-Type: application/json` is set for you:

```kotlin
@Serializable
data class NewUser(val name: String)

@Service
interface UserService {
    @POST("users")
    suspend fun createUser(@Body user: NewUser): User
}

val response = service.createUser(NewUser(name = "Alice"))   // RestResponse<User>
```

### Custom per-request headers

Per-request headers always take priority over `defaultHeaders`:

```kotlin
@Service
interface UserService {
    @GET("users/{id}")
    suspend fun getUser(
        @Path("id") id: Int,
        @Header("X-Request-ID") requestId: String,
    ): User
}

val response = service.getUser(id = 1, requestId = UUID.randomUUID().toString())
```

## Constructing a client

The generated `<Name>Client` offers two constructors. Only `baseUrl` is required; every other option is optional:

```kotlin
// 1. Owns its RestClient — pass the options directly.
val users = UserServiceClient(baseUrl = "https://api.example.com/")

// 2. Shares an existing RestClient across several services (one token-refresh loop for all).
val client = RestClient(baseUrl = "https://api.example.com/")
val users = UserServiceClient(client)
val orders = OrderServiceClient(client)
```

> **Note:** Call `close()` when a client is no longer needed (e.g. on logout or `ViewModel.onCleared()`) to cancel the background refresh coroutine and release OkHttp resources. A client created with constructor (1) closes its `RestClient`; a client created with constructor (2) leaves the shared `RestClient` open — close that yourself.

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

Connection-level behaviour (headers, timeouts, auth) is controlled through the client's constructor arguments; per-endpoint caching and retry are controlled with annotations on the service interface (see below).

### Default headers

Headers added to every request. A header already present on an individual request takes priority.

```kotlin
val service = UserServiceClient(
    baseUrl = "https://api.example.com/",
    defaultHeaders = mapOf(
        "Accept" to "application/json",
        "X-API-Version" to "2",
    ),
)
```

### Retry

Retry is opt-in per endpoint via `@Retry`. Annotate the service interface to set a default for every idempotent method, or a single method to override it; `@NoRetry` disables retries for a method that would otherwise inherit the service default. Retries trigger on network failures and 5xx server errors. `delay` is in seconds; a request with no `@Retry` is attempted exactly once.

Only idempotent verbs (GET, PUT, DELETE) may be retried — putting `@Retry`/`@NoRetry` on a `@POST`/`@PATCH` method is a compile-time error.

```kotlin
@Service
@Retry(maxAttempts = 3, delay = 1) // default for all idempotent methods
interface UserService {
    @GET("users/{id}")
    @Retry(maxAttempts = 5, delay = 2) // override for this endpoint
    suspend fun getUser(@Path("id") id: Int): User

    @GET("health")
    @NoRetry // never retried
    suspend fun health(): Status
}
```

### Caching

GET responses can be cached in memory via `@Cacheable`. Annotate the service interface to cache every GET method by default, or a single method to override it; `@NoCache` disables caching for a method that would otherwise inherit the service default. The second call with the same URL returns the cached response without hitting the network. `ttl` is in seconds and defaults to never expiring; `maxEntries` defaults to unlimited (and sizes the shared cache, so it is honored only at service level).

```kotlin
@Service
@Cacheable(ttl = 60, maxEntries = 100) // maxEntries sizes the shared cache (service-level only)
interface UserService {
    @GET("users/{id}")
    @Cacheable(ttl = 300) // override: cache this endpoint for 5 minutes
    suspend fun getUser(@Path("id") id: Int): User

    @GET("health")
    @NoCache // always hit the network
    suspend fun health(): Status
}
```

## Authentication

### Attaching a token to every request

Use `tokenProvider` to supply the current token. Once configured, every request automatically receives an `Authorization: Bearer <token>` header.

```kotlin
val service = UserServiceClient(
    baseUrl = "https://api.example.com/",
    tokenProvider = { authStore.accessToken },
)
```

### Skipping auth on specific endpoints

Annotate a service method with `@SkipAuth` to opt out of token injection — useful for login or public endpoints:

```kotlin
import io.vinicius.sak.rest.annotation.Service
import io.vinicius.sak.rest.annotation.SkipAuth

@Service
interface AuthService {
    @SkipAuth
    @POST("auth/login")
    suspend fun login(@Body credentials: Credentials): LoginResponse

    @POST("auth/logout")
    suspend fun logout(): Unit   // token is injected normally
}
```

### Automatic token refresh on 401

Provide `tokenRefresher` to fetch a new token when a 401 is received. The client refreshes the token once and retries the original request automatically. Concurrent requests that all hit 401 share a single refresh call.

```kotlin
val service = UserServiceClient(
    baseUrl = "https://api.example.com/",
    tokenProvider = { authStore.accessToken },
    tokenRefresher = {
        val newToken = authApi.refresh(authStore.refreshToken)
        authStore.accessToken = newToken
        true   // return false to signal refresh failure
    },
)
```

### Preemptive JWT refresh

Avoid 401 errors entirely by refreshing the token before it expires. Use `preemptiveRefresh` to control how far in advance to refresh (default: 60 seconds). The client polls the token expiry in the background and refreshes automatically when it falls within the threshold.

```kotlin
val service = UserServiceClient(
    baseUrl = "https://api.example.com/",
    tokenProvider = { authStore.accessToken },
    tokenRefresher = {
        val newToken = authApi.refresh(authStore.refreshToken)
        authStore.accessToken = newToken
        true
    },
    preemptiveRefresh = 60.seconds,   // refresh 60 s before expiry
)
```

## Key types

| Type | Role |
|------|------|
| `@Service` | Annotation on an interface — generates a `<Name>Client` |
| `<Name>Client` | Generated client — construct with `baseUrl` + options, or a shared `RestClient` |
| `RestClient` | Underlying engine — construct with `baseUrl` + options; share one across services via the client's secondary constructor |
| `@Cacheable` / `@NoCache` | Per-endpoint (or service-level) in-memory caching: `ttl` (seconds), `maxEntries` |
| `@Retry` / `@NoRetry` | Per-endpoint (or service-level) retry: `maxAttempts`, `delay` (seconds); idempotent verbs only |
| `RestResponse<T>` | Decoded response body + `statusCode` + `headers` |
| `RestError` | Sealed error hierarchy thrown on failure |
| `@SkipAuth` | Annotation to skip auth injection on a single endpoint |