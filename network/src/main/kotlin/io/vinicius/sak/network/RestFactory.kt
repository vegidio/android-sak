package io.vinicius.sak.network

import android.content.Context
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.logging.HttpLoggingInterceptor.Level
import okhttp3.logging.HttpLoggingInterceptor.Logger
import retrofit2.CallAdapter
import retrofit2.Converter
import retrofit2.Retrofit
import kotlin.reflect.KClass
import kotlin.time.Duration

/**
 * Log configuration.
 *
 * @param logger define how the log should be output.
 * @param level defines the level of information that should be output.
 */
typealias LogHandler = Pair<Logger, Level>

/**
 * Cache configuration.
 *
 * @param size defines the maximum cache storage, in bytes.
 * @param duration defines the maximum duration of the cached data.
 */
typealias CacheConfig = Pair<Long, Duration>

class RestFactory constructor(
    private val context: Context,
    var converter: Converter.Factory? = null,
    var callAdapter: CallAdapter.Factory? = null,
    var logHandler: LogHandler? = LogHandler(Logger.DEFAULT, Level.BASIC)
) {
    fun <T : Any> create(klass: KClass<T>, baseUrl: String, cacheConfig: CacheConfig? = null): T {
        val client = OkHttpClient.Builder()

        // Adding the logger
        logHandler?.let { (logger, level) ->
            client.addInterceptor(createLogInterceptor(logger, level))
        }

        // Adding cache support
        cacheConfig?.let { (size, duration) ->
            client.cache(Cache(context.cacheDir, size)).addInterceptor(createCachePolicyInterceptor(duration))
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

        return retrofit.build().create(klass.java)
    }

    // region - Private methods
    private fun createLogInterceptor(logger: Logger, level: Level) =
        HttpLoggingInterceptor(logger).apply { setLevel(level) }

    private fun createCachePolicyInterceptor(duration: Duration) = Interceptor {
        val request = it.request().newBuilder()
            .header("Cache-Control", "public, max-stale=${duration.inWholeSeconds}")
            .build()

        it.proceed(request)
    }
    // endregion
}