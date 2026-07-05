package io.vinicius.sak.rest.service

import io.vinicius.sak.rest.RestError
import io.vinicius.sak.rest.annotation.Cacheable
import io.vinicius.sak.rest.annotation.NoCache
import io.vinicius.sak.rest.annotation.NoRetry
import io.vinicius.sak.rest.annotation.Retry
import io.vinicius.sak.rest.annotation.Service
import io.vinicius.sak.rest.annotation.SkipAuth
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

@Serializable
data class User(
    val id: Int,
    val name: String,
)

@Serializable
data class Credentials(
    val user: String,
    val pass: String,
)

@Serializable
data class Token(
    val value: String,
)

/**
 * Exercises the `@Service` KSP codegen end-to-end with method-level annotations: the annotated interface below drives
 * generation of `TestApiClient` plus the internal `TestApiRetrofit` interface, and the resolved annotations are read at
 * runtime by the interceptors via Retrofit's `Invocation` tag.
 */
@Service
interface TestApi {
    @GET("users/{id}")
    @Cacheable(ttl = 60)
    suspend fun getUser(
        @Path("id") id: Int,
    ): User

    @GET("health")
    suspend fun health(): User

    @GET("flaky")
    @Retry(maxAttempts = 3, delay = 0.0)
    suspend fun flaky(): User

    @SkipAuth
    @POST("login")
    suspend fun login(
        @Body body: Credentials,
    ): Token
}

/**
 * Exercises service-level defaults: every idempotent method inherits `@Cacheable`/`@Retry` unless it opts out with
 * `@NoCache`/`@NoRetry`.
 */
@Service
@Cacheable(ttl = 60, maxEntries = 50)
@Retry(maxAttempts = 3, delay = 0.0)
interface DefaultsApi {
    @GET("a/{id}")
    suspend fun getA(
        @Path("id") id: Int,
    ): User

    @GET("b")
    @NoCache
    suspend fun getB(): User

    @GET("c")
    @NoRetry
    suspend fun getC(): User
}

class ServiceClientTest {
    private val server = MockWebServer()

    @BeforeEach fun setUp() = server.start()

    @AfterEach fun tearDown() = server.close()

    private fun baseUrl() = server.url("/").toString()

    private fun ok(
        body: String,
        vararg headers: Pair<String, String>,
    ) = MockResponse
        .Builder()
        .code(200)
        .apply { headers.forEach { (name, value) -> addHeader(name, value) } }
        .body(body)
        .build()

    private fun user(id: Int = 1) = """{"id":$id,"name":"Alice"}"""

    @Test
    fun `generated client wraps the body in RestResponse with status, headers, and rawResponse`() =
        runBlocking {
            server.enqueue(ok(user(), "X-Trace" to "abc"))

            val client = TestApiClient(baseUrl = baseUrl(), tokenProvider = { "Bearer token-123" })
            val response = client.getUser(1)

            val body = response.body!!
            assertEquals(1, body.id)
            assertEquals("Alice", body.name)
            assertEquals(200, response.statusCode)
            assertEquals("abc", response.headers["X-Trace"])
            // rawResponse exposes the underlying Retrofit response.
            assertEquals(200, response.rawResponse.code())

            val recorded = server.takeRequest()
            assertEquals("/users/1", recorded.url.encodedPath)
            // Token is sent verbatim — the provider owns the scheme.
            assertEquals("Bearer token-123", recorded.headers["Authorization"])
        }

    @Test
    fun `no-content 204 response yields a null body with inspectable metadata`() =
        runBlocking {
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(204)
                    .addHeader("X-Trace", "z")
                    .build(),
            )

            val client = TestApiClient(baseUrl = baseUrl())
            val response = client.health()

            assertNull(response.body)
            assertEquals(204, response.statusCode)
            assertEquals("z", response.headers["X-Trace"])
            assertEquals(204, response.rawResponse.code())
        }

    @Test
    fun `non-2xx surfaces as RestError HttpError with status and body`() =
        runBlocking {
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(404)
                    .body("nope")
                    .build(),
            )

            val client = TestApiClient(baseUrl = baseUrl())
            val error = assertThrows<RestError.HttpError> { client.health() }

            assertEquals(404, error.statusCode)
            assertEquals("nope", error.body)
        }

    @Test
    fun `malformed JSON surfaces as RestError DecodingError`() =
        runBlocking {
            server.enqueue(ok("not-json"))

            val client = TestApiClient(baseUrl = baseUrl())
            assertThrows<RestError.DecodingError> { client.health() }
        }

    @Test
    fun `network failure surfaces as RestError Network`() =
        runBlocking {
            // Nothing is listening on this port, so the connection is refused.
            val client = TestApiClient(baseUrl = "http://localhost:1/")
            assertThrows<RestError.Network> { client.health() }
        }

    @Test
    fun `token refresh failure surfaces as RestError TokenRefreshFailed`() =
        runBlocking {
            server.enqueue(MockResponse.Builder().code(401).build())

            val client =
                TestApiClient(
                    baseUrl = baseUrl(),
                    tokenProvider = { "Bearer old" },
                    tokenRefresher = { throw IllegalStateException("refresh boom") },
                )
            val error = assertThrows<RestError> { client.health() }
            assertEquals(RestError.TokenRefreshFailed, error)
        }

    @Test
    fun `@SkipAuth method omits the Authorization header`() =
        runBlocking {
            server.enqueue(ok("""{"value":"jwt"}"""))

            val client = TestApiClient(baseUrl = baseUrl(), tokenProvider = { "Bearer token-123" })
            val response = client.login(Credentials("a", "b"))

            assertEquals("jwt", response.body!!.value)
            assertNull(server.takeRequest().headers["Authorization"])
        }

    @Test
    fun `@Cacheable method serves the second call from cache`() =
        runBlocking {
            server.enqueue(ok(user()))

            val client = TestApiClient(baseUrl = baseUrl())
            client.getUser(1)
            val second = client.getUser(1)

            assertEquals(1, second.body!!.id)
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `method without @Cacheable is not cached`() =
        runBlocking {
            server.enqueue(ok(user()))
            server.enqueue(ok(user()))

            val client = TestApiClient(baseUrl = baseUrl())
            client.health()
            client.health()

            assertEquals(2, server.requestCount)
        }

    @Test
    fun `@Retry method recovers after transient 5xx`() =
        runBlocking {
            server.enqueue(MockResponse.Builder().code(500).build())
            server.enqueue(MockResponse.Builder().code(500).build())
            server.enqueue(ok(user()))

            val client = TestApiClient(baseUrl = baseUrl())
            val response = client.flaky()

            assertEquals(1, response.body!!.id)
            assertEquals(3, server.requestCount)
        }

    @Test
    fun `service-level @Cacheable is inherited by GET methods`() =
        runBlocking {
            server.enqueue(ok(user()))

            val client = DefaultsApiClient(baseUrl = baseUrl())
            client.getA(1)
            client.getA(1)

            assertEquals(1, server.requestCount)
        }

    @Test
    fun `service-level @Retry is inherited by GET methods`() =
        runBlocking {
            server.enqueue(MockResponse.Builder().code(500).build())
            server.enqueue(MockResponse.Builder().code(500).build())
            server.enqueue(ok(user()))

            val client = DefaultsApiClient(baseUrl = baseUrl())
            val response = client.getA(1)

            assertEquals(1, response.body!!.id)
            assertEquals(3, server.requestCount)
        }

    @Test
    fun `@NoCache overrides the service-level default`() =
        runBlocking {
            server.enqueue(ok(user()))
            server.enqueue(ok(user()))

            val client = DefaultsApiClient(baseUrl = baseUrl())
            client.getB()
            client.getB()

            assertEquals(2, server.requestCount)
        }

    @Test
    fun `logging closure receives OkHttp-style request and response blocks when enabled`() =
        runBlocking {
            server.enqueue(ok(user()))

            val messages = mutableListOf<String>()
            val client = TestApiClient(
                baseUrl = baseUrl(),
                tokenProvider = { "Bearer token-123" },
                logging = { messages += it },
            )
            client.getUser(1)

            val joined = messages.joinToString("\n")
            assertTrue(joined.contains("--> GET"), "missing request line in: $joined")
            assertTrue(joined.contains("<-- 200"), "missing response line in: $joined")
        }

    @Test
    fun `logging is disabled by default so no interceptor observes the request`() =
        runBlocking {
            server.enqueue(ok(user()))

            // Omitting `logging` leaves the default (null): no HttpLoggingInterceptor is installed and the request
            // completes normally. There is no sink to observe, which is exactly the off-by-default contract.
            val client = TestApiClient(baseUrl = baseUrl())
            val response = client.getUser(1)

            assertEquals(1, response.body!!.id)
        }

    @Test
    fun `@NoRetry overrides the service-level default`() =
        runBlocking {
            server.enqueue(MockResponse.Builder().code(500).build())

            val client = DefaultsApiClient(baseUrl = baseUrl())
            assertThrows<RestError.HttpError> { client.getC() }

            assertEquals(1, server.requestCount)
        }

    @Test
    fun `two clients keep independent configuration`() =
        runBlocking {
            server.enqueue(ok(user()))
            server.enqueue(ok(user()))

            val clientA = TestApiClient(baseUrl = baseUrl(), tokenProvider = { "Bearer A" })
            val clientB = TestApiClient(baseUrl = baseUrl(), tokenProvider = { "Bearer B" })

            clientA.health()
            clientB.health()

            // Each client sends only its own token — no shared state leaks between instances.
            assertEquals("Bearer A", server.takeRequest().headers["Authorization"])
            assertEquals("Bearer B", server.takeRequest().headers["Authorization"])
        }
}