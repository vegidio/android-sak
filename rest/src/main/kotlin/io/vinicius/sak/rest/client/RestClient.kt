package io.vinicius.sak.rest.client

import retrofit2.Retrofit

/**
 * Builds and holds a configured [Retrofit] instance.
 *
 * Responsibilities:
 * - Creates an [okhttp3.OkHttpClient] with logging and auth interceptors
 * - Configures the JSON converter (Gson by default)
 * - Exposes [createService] for generating type-safe API service interfaces
 */
class RestClient(private val baseUrl: String) {

    val retrofit: Retrofit by lazy {
        // TODO: build OkHttpClient:
        //   val httpClient = OkHttpClient.Builder()
        //       .addInterceptor(HttpLoggingInterceptor().apply { level = BODY })
        //       .addInterceptor(AuthInterceptor(token))
        //       .connectTimeout(30, TimeUnit.SECONDS)
        //       .readTimeout(30, TimeUnit.SECONDS)
        //       .build()

        // TODO: build Retrofit:
        //   Retrofit.Builder()
        //       .baseUrl(baseUrl)
        //       .client(httpClient)
        //       .addConverterFactory(GsonConverterFactory.create())
        //       .build()

        TODO("implement Retrofit setup")
    }

    /**
     * Creates a type-safe implementation of the given API service interface.
     *
     * Example:
     * ```
     * val service = restClient.createService<UserApiService>()
     * ```
     */
    inline fun <reified T> createService(): T {
        // TODO: return retrofit.create(T::class.java)
        TODO("implement service creation")
    }
}
