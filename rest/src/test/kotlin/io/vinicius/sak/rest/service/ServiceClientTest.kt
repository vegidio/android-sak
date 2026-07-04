package io.vinicius.sak.rest.service

import io.vinicius.sak.rest.annotation.Service
import io.vinicius.sak.rest.annotation.SkipAuth
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
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
 * Exercises the `@Service` KSP codegen end-to-end: the annotated interface below drives generation of `TestApiClient`
 * (used directly here) plus the internal `TestApiRetrofit` interface.
 */
@Service
interface TestApi {
    @GET("users/{id}")
    suspend fun getUser(
        @Path("id") id: Int,
    ): User

    @SkipAuth
    @POST("login")
    suspend fun login(
        @Body body: Credentials,
    ): Token
}

class ServiceClientTest {
    private val server = MockWebServer()

    @Before fun setUp() = server.start()

    @After fun tearDown() = server.close()

    private fun baseUrl() = server.url("/").toString()

    @Test
    fun `generated client wraps the body in RestResponse with status and headers`() =
        runBlocking {
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(200)
                    .addHeader("X-Trace", "abc")
                    .body("""{"id":1,"name":"Alice"}""")
                    .build(),
            )

            val client = TestApiClient(baseUrl = baseUrl(), tokenProvider = { "token-123" })
            val response = client.getUser(1)

            assertEquals(1, response.body.id)
            assertEquals("Alice", response.body.name)
            assertEquals(200, response.statusCode)
            assertEquals("abc", response.headers["X-Trace"])

            val recorded = server.takeRequest()
            assertEquals("/users/1", recorded.url.encodedPath)
            assertEquals("Bearer token-123", recorded.headers["Authorization"])
            client.close()
        }

    @Test
    fun `@SkipAuth method omits the Authorization header`() =
        runBlocking {
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(200)
                    .body("""{"value":"jwt"}""")
                    .build(),
            )

            val client = TestApiClient(baseUrl = baseUrl(), tokenProvider = { "token-123" })
            val response = client.login(Credentials("a", "b"))

            assertEquals("jwt", response.body.value)
            assertNull(server.takeRequest().headers["Authorization"])
            client.close()
        }
}