package io.vinicius.sak.network

enum class NetworkState(var value: Any? = null) {
    Idle,
    Loading,
    Error;

    companion object {
        fun error(data: Any) = NetworkState.Error.apply {
            this.value = data
        }
    }
}

@Suppress("Unchecked_Cast")
fun <T> NetworkState.data() = this.value as T