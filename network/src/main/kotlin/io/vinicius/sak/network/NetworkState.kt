package io.vinicius.sak.network

sealed class NetworkState {
    object Idle : NetworkState()
    object Loading : NetworkState()

    data class Error(val throwable: Throwable = Error("Unknown error")) : NetworkState() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Error) return false
            return true
        }

        override fun hashCode(): Int = 1
    }

    val error: Throwable? get() = if (this is NetworkState.Error) this.throwable else null
}