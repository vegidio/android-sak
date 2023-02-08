package io.vinicius.sak.network

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import retrofit2.Call
import retrofit2.CallAdapter
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.concurrent.atomic.AtomicBoolean

class FlowCallAdapterFactory : CallAdapter.Factory() {
    override fun get(returnType: Type, annotations: Array<Annotation>, retrofit: Retrofit): CallAdapter<*, *>? {
        if (getRawType(returnType) != Flow::class.java) {
            return null
        }

        val observableType = getParameterUpperBound(0, returnType as ParameterizedType)
        return FlowCallAdapter<Any>(observableType)
    }
}

private class FlowCallAdapter<R>(private val responseType: Type) : CallAdapter<R, Flow<R?>> {
    private val httpNoContent = 204
    override fun responseType() = responseType

    override fun adapt(call: Call<R>): Flow<R?> = callbackFlow {
        val started = AtomicBoolean(false)

        if (started.compareAndSet(false, true)) {
            call.enqueue(object : Callback<R> {
                override fun onResponse(call: Call<R>, response: Response<R>) {
                    if (response.isSuccessful) {
                        val body = response.body()

                        if (body == null || response.code() == httpNoContent) {
                            close(Throwable("HTTP status code: ${response.code()}"))
                        } else {
                            trySend(body)
                            close()
                        }
                    } else {
                        close(Throwable(errorMsg(response) ?: "unknown error"))
                    }
                }

                override fun onFailure(call: Call<R>, throwable: Throwable) {
                    close(throwable)
                }
            })
        }

        awaitClose { call.cancel() }
    }

    private fun errorMsg(response: Response<R>): String? {
        val msg = response.errorBody()?.string()
        return if (msg.isNullOrEmpty()) {
            response.message()
        } else {
            msg
        }
    }
}