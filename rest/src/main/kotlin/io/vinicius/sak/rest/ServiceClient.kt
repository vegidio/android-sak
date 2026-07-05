package io.vinicius.sak.rest

import okhttp3.Response
import retrofit2.Retrofit
import kotlin.time.Duration

/**
 * Public base class for the clients generated from an [io.vinicius.sak.rest.annotation.Service] interface. It owns the
 * underlying HTTP engine ([RestClient], an internal implementation detail) and exposes the configured [retrofit] to its
 * subclasses.
 *
 * This class exists purely as the seam between a generated `<Name>Client` and the `:rest` runtime: the KSP-generated
 * client extends it and forwards the constructor options. Its constructor is `protected` and it holds no public state,
 * so application code never instantiates it directly — developers use only the generated `<Name>Client` types.
 *
 * @see io.vinicius.sak.rest.annotation.Service
 */
@Suppress("LongParameterList")
abstract class ServiceClient protected constructor(
    baseUrl: String,
    defaultHeaders: Map<String, String>,
    cacheMaxEntries: Int,
    tokenProvider: (suspend () -> String?)?,
    tokenRefresher: (suspend () -> String)?,
    preemptiveRefresh: Duration?,
    isUnauthorized: (Response) -> Boolean,
    connectTimeout: Duration,
    readTimeout: Duration,
    logging: ((String) -> Unit)?,
) {
    /** The configured Retrofit instance the generated subclass builds its service proxy from. */
    protected val retrofit: Retrofit =
        RestClient(
            baseUrl = baseUrl,
            defaultHeaders = defaultHeaders,
            cacheMaxEntries = cacheMaxEntries,
            tokenProvider = tokenProvider,
            tokenRefresher = tokenRefresher,
            preemptiveRefresh = preemptiveRefresh,
            isUnauthorized = isUnauthorized,
            connectTimeout = connectTimeout,
            readTimeout = readTimeout,
            logging = logging,
        ).retrofit
}