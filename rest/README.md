# rest

A high-level HTTP client for REST APIs built on [Retrofit](https://github.com/square/retrofit) and [OkHttp](https://github.com/square/okhttp). Handles retry, caching, default headers, and automatic token refresh so you only write request logic.

You declare your API as an interface annotated with `@Service`; at compile time the `rest-compiler` KSP processor generates a `<Name>Client` for you. You declare each method's **body type** as its return type, and the generated client returns that body wrapped in a `RestResponse<T>` (body + status code + headers + the raw Retrofit response).

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
println(response.body?.name)        // "Alice" (body is null for 204/205 no-content responses)
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

Construct the generated `<Name>Client` directly — only `baseUrl` is required; every other option is optional. Each client is standalone (there is no shared engine to manage or pass around):

```kotlin
val users = UserServiceClient(baseUrl = "https://api.example.com/")
val orders = OrderServiceClient(baseUrl = "https://api.example.com/")
```

> **Note:** There is no lifecycle to manage — the client holds no background threads, so there is no `close()` to call. Preemptive token refresh is evaluated per request, and OkHttp releases idle connections and threads on its own.

## Error handling

All failures are thrown as subtypes of `RestError`:

| Type                          | When                                                     |
|-------------------------------|----------------------------------------------------------|
| `InvalidUrl(url)`             | The `baseUrl` is malformed or does not end in `/`        |
| `Network(cause)`              | A transport-level failure (no connection, timeout, etc.) |
| `HttpError(statusCode, body)` | Server returned a non-2xx status after all retries       |
| `DecodingError(cause)`        | Response body could not be decoded into `T`              |
| `TokenRefreshFailed`          | A token refresh was attempted but `tokenRefresher` threw |

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

### Timeouts

`connectTimeout` and `readTimeout` (the latter also applies to writes) are OkHttp `Duration` values, each defaulting to 30 seconds:

```kotlin
val service = UserServiceClient(
    baseUrl = "https://api.example.com/",
    connectTimeout = 10.seconds,
    readTimeout = 60.seconds,
)
```

### Logging

Logging is off by default. Pass a `logging: ((String) -> Unit)?` sink to receive OkHttp-style request/response text; omitting it installs no logging interceptor at all. When set, requests are logged at **BODY** level — including full request/response bodies and the injected `Authorization` header — so gate the sink behind a debug/build-type check to avoid leaking tokens or PII in production:

```kotlin
val service = UserServiceClient(
    baseUrl = "https://api.example.com/",
    logging = if (BuildConfig.DEBUG) { message -> Log.d("REST", message) } else null,
)
```

### Retry

Retry is opt-in per endpoint via `@Retry`. Annotate the service interface to set a default for every idempotent method, or a single method to override it; `@NoRetry` disables retries for a method that would otherwise inherit the service default. Retries trigger on network failures and 5xx server errors. `delay` is in seconds as a `Double`, so fractional values like `0.5` are allowed; a request with no `@Retry` is attempted exactly once.

Only idempotent verbs (GET, PUT, DELETE) may be retried — putting `@Retry`/`@NoRetry` on a `@POST`/`@PATCH` method is a compile-time error.

```kotlin
@Service
@Retry(maxAttempts = 3, delay = 1.0) // default for all idempotent methods
interface UserService {
    @GET("users/{id}")
    @Retry(maxAttempts = 5, delay = 0.5) // override for this endpoint
    suspend fun getUser(@Path("id") id: Int): User

    @GET("health")
    @NoRetry // never retried
    suspend fun health(): Status
}
```

### Caching

GET responses can be cached in memory via `@Cacheable`. Annotate the service interface to cache every GET method by default, or a single method to override it; `@NoCache` disables caching for a method that would otherwise inherit the service default. The second call with the same URL returns the cached response without hitting the network. `ttl` is in seconds (`Long`) and defaults to never expiring; `maxEntries` defaults to unlimited (and sizes the shared cache, so it is honored only at service level).

Two constraints are enforced at compile time: a **method-level** `@Cacheable` may only be applied to a GET method, and `maxEntries` may only be set on the service-level annotation (setting it on a method is a compile-time error).

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

Use `tokenProvider` to supply the current `Authorization` header value. The value is used **verbatim** — your closure owns the scheme (`"Bearer …"`, `"Token …"`, or anything else), and nothing is prepended for you. Once configured, every request that isn't `@SkipAuth` receives that value as its `Authorization` header.

```kotlin
val service = UserServiceClient(
    baseUrl = "https://api.example.com/",
    tokenProvider = { "Bearer ${authStore.accessToken}" },
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

### Automatic token refresh on an auth failure

Provide `tokenRefresher` to fetch a new token when a request comes back unauthorized. It **returns the new verbatim `Authorization` header value** and must **throw** on failure (a failure surfaces to the caller as `RestError.TokenRefreshFailed`). The client refreshes the token once and retries the original request automatically; concurrent requests that all fail share a single refresh call.

```kotlin
val service = UserServiceClient(
    baseUrl = "https://api.example.com/",
    tokenProvider = { "Bearer ${authStore.accessToken}" },
    tokenRefresher = {
        val newToken = authApi.refresh(authStore.refreshToken)   // throws on failure
        authStore.accessToken = newToken
        "Bearer $newToken"   // the new header value, applied to the retry
    },
)
```

What counts as an "auth failure" is configurable via `isUnauthorized: (okhttp3.Response) -> Boolean`, defaulting to HTTP 401. Override it to treat another status as a refresh trigger:

```kotlin
val service = UserServiceClient(
    baseUrl = "https://api.example.com/",
    tokenProvider = { "Bearer ${authStore.accessToken}" },
    tokenRefresher = { "Bearer ${authApi.refresh(authStore.refreshToken)}" },
    isUnauthorized = { it.code == 401 || it.code == 419 },
)
```

### Preemptive JWT refresh

Avoid auth failures entirely by refreshing the token before it expires. Use `preemptiveRefresh` to control how far in advance to refresh (default: 60 seconds). On **every request**, the client reads the JWT `exp` claim from the current token and, if it falls within the window, refreshes before sending — no background thread is involved. Opaque (non-JWT) tokens have no readable expiry and are only refreshed reactively. Set `preemptiveRefresh = null` (or `Duration.ZERO`) to disable it.

```kotlin
val service = UserServiceClient(
    baseUrl = "https://api.example.com/",
    tokenProvider = { "Bearer ${authStore.accessToken}" },
    tokenRefresher = {
        val newToken = authApi.refresh(authStore.refreshToken)
        authStore.accessToken = newToken
        "Bearer $newToken"
    },
    preemptiveRefresh = 60.seconds,   // refresh within 60 s of expiry
)
```

## Key types

| Type                      | Role                                                                                                      |
|---------------------------|-----------------------------------------------------------------------------------------------------------|
| `@Service`                | Annotation on an interface — generates a `<Name>Client`                                                   |
| `<Name>Client`            | Generated client — the only entry point; construct with `baseUrl` + options                               |
| `@Cacheable` / `@NoCache` | Per-endpoint (or service-level) in-memory caching: `ttl` (seconds), `maxEntries`                          |
| `@Retry` / `@NoRetry`     | Per-endpoint (or service-level) retry: `maxAttempts`, `delay` (seconds, `Double`); idempotent verbs only  |
| `RestResponse<T>`         | Nullable `body` (null on 204/205) + `statusCode` + `headers` + `rawResponse` (raw Retrofit `Response<T>`) |
| `RestError`               | Sealed error hierarchy thrown on every failure by the generated client                                    |
| `@SkipAuth`               | Annotation to skip auth injection on a single endpoint                                                    |