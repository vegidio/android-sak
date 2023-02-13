package io.vinicius.sak.network.internal

import com.apollographql.apollo3.api.ApolloRequest
import com.apollographql.apollo3.api.ApolloResponse
import com.apollographql.apollo3.api.Operation
import com.apollographql.apollo3.api.http.HttpHeader
import com.apollographql.apollo3.interceptor.ApolloInterceptor
import com.apollographql.apollo3.interceptor.ApolloInterceptorChain
import kotlinx.coroutines.flow.Flow

@PublishedApi
internal class GraphqlHeaderInterceptor : ApolloInterceptor {
    val headers: MutableMap<String, String> = mutableMapOf()
    val requestHeaders: MutableMap<String, Map<String, String>> = mutableMapOf()

    override fun <D : Operation.Data> intercept(
        request: ApolloRequest<D>,
        chain: ApolloInterceptorChain
    ): Flow<ApolloResponse<D>> {
        val name = request.operation.name()
        val headerList = headers.map { (key, value) -> HttpHeader(key, value) }
        val tempHeaderList = requestHeaders[name].orEmpty().map { (key, value) -> HttpHeader(key, value) }
        val finalHeaderList = (headerList + tempHeaderList).distinctBy { it.name }

        val newRequest = request.newBuilder()
            .httpHeaders(finalHeaderList)
            .build()

        requestHeaders.remove(name)
        return chain.proceed(newRequest)
    }
}