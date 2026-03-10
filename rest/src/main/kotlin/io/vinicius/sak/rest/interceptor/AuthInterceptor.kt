package io.vinicius.sak.rest.interceptor

import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp interceptor that injects an Authorization header into every outgoing request.
 *
 * Usage: add to [okhttp3.OkHttpClient.Builder] via [okhttp3.OkHttpClient.Builder.addInterceptor].
 */
class AuthInterceptor(private val token: String) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        // TODO: build new request with Authorization header:
        //   val request = chain.request().newBuilder()
        //       .addHeader("Authorization", "Bearer $token")
        //       .build()
        //   return chain.proceed(request)

        TODO("implement auth interception")
    }
}
