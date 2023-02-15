package io.vinicius.sak.network

import android.content.Context
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.logging.HttpLoggingInterceptor.Level
import retrofit2.CallAdapter
import retrofit2.Converter
import retrofit2.Retrofit
import kotlin.reflect.KClass
import kotlin.time.Duration

/**
 * Log configuration.
 *
 * @param logger the default output of the log.
 * @param level the level of information that is output; NONE, BASIC, HEADERS, BODY
 */
typealias LogHandler = Pair<(String) -> Unit, String>

/**
 * Cache configuration.
 *
 * @param context the app context; used to save cached data in disk.
 * @param duration the maximum duration of the cached data.
 */
typealias CacheConfig = Pair<Context, Duration>

open class RestFactory<T : Any>(
    klass: KClass<T>,
    baseUrl: String,
    converter: Converter.Factory? = GeneralConverterFactory.moshi(),
    callAdapter: CallAdapter.Factory? = FlowCallAdapterFactory(),
    logHandler: LogHandler? = null,
    cacheConfig: CacheConfig? = null,
) {
    // Instance of API endpoints
    protected val api: T

    var headers: MutableMap<String, String> = mutableMapOf()

    init {
        val client = OkHttpClient.Builder()
            .addInterceptor(createHeadersInterceptor())

        // Adding the logger
        logHandler?.let { (logger, level) ->
            client.addInterceptor(createLogInterceptor(logger, level))
        }

        // Adding cache support
        cacheConfig?.let { (context, duration) ->
            client.cache(Cache(context.cacheDir, maxSize = 10_000_000))
                .addInterceptor(createCachePolicyInterceptor(duration))
        }

        val retrofit = Retrofit.Builder()
            .client(client.build())
            .baseUrl(baseUrl)

        // Adding the converter
        converter?.let {
            retrofit.addConverterFactory(it)
        }

        // Adding the call adapter
        callAdapter?.let {
            retrofit.addCallAdapterFactory(it)
        }

        api = retrofit.build().create(klass.java)
    }

    // region - Private methods
    private fun createLogInterceptor(logger: (String) -> Unit, level: String) =
        HttpLoggingInterceptor { logger(it) }.apply { setLevel(Level.valueOf(level)) }

    private fun createHeadersInterceptor() = Interceptor {
        val request = it.request().newBuilder()
        headers.forEach { (key, value) -> request.addHeader(key, value) }

        it.proceed(request.build())
    }

    private fun createCachePolicyInterceptor(duration: Duration) = Interceptor {
        val request = it.request().newBuilder()
            .header("Cache-Control", "public, max-stale=${duration.inWholeSeconds}")
            .build()

        it.proceed(request)
    }
    // endregion
}