package io.vinicius.sak.network

import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.Mutation
import com.apollographql.apollo3.api.Query
import com.apollographql.apollo3.api.toJson
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import io.vinicius.sak.network.internal.GraphqlHeaderInterceptor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flowOf
import org.json.JSONObject
import kotlin.reflect.KClass

@OptIn(ExperimentalCoroutinesApi::class)
open class GraphqlFactory(url: String, vararg val types: KClass<*>) {
    @PublishedApi internal val moshi = Moshi.Builder().build()
    @PublishedApi internal val headerInterceptor = GraphqlHeaderInterceptor()
    @PublishedApi internal val client = ApolloClient.Builder()
        .serverUrl(url)
        .addInterceptor(headerInterceptor)
        .build()

    var headers: MutableMap<String, String>
        get() = headerInterceptor.headers
        set(value) = headerInterceptor.headers.putAll(value)

    inline fun <reified T> sendQuery(
        query: Query<*>,
        headers: Map<String, String> = emptyMap()
    ): Flow<T> {
        val name = query.name()
        headerInterceptor.requestHeaders[name] = headers

        return client.query(query).toFlow()
            .flatMapConcat {
                val obj = deserializeObject<T>(it.data?.toJson())
                    ?: error("Error deserializing object ${T::class.qualifiedName}")
                flowOf(obj)
            }
    }

    inline fun <reified T> sendMutation(
        mutation: Mutation<*>,
        headers: Map<String, String> = emptyMap()
    ): Flow<T> {
        val name = mutation.name()
        headerInterceptor.requestHeaders[name] = headers

        return client.mutation(mutation).toFlow()
            .flatMapConcat {
                val obj = deserializeObject<T>(it.data?.toJson())
                    ?: error("Error deserializing object ${T::class.qualifiedName}")
                flowOf(obj)
            }
    }

    @Suppress("SpreadOperator")
    @PublishedApi
    internal inline fun <reified T> deserializeObject(json: String?): T? {
        if (json == null) return null

        val wrapper = JSONObject(json)
        val key = wrapper.keys().next()
        val jsonObject = wrapper.getJSONObject(key)

        wrapper.remove(key)
        wrapper.put("data", jsonObject)

        val subTypes = types.map { it.java }.toTypedArray()
        val parameterizedType = Types.newParameterizedType(T::class.java, *subTypes)
        val adapter = moshi.adapter<T>(parameterizedType)

        return adapter.fromJson(wrapper.toString())
    }
}