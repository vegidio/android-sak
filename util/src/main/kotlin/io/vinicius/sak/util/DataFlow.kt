package io.vinicius.sak.util

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

// Based on the best practice of not exposing mutable types:
// https://developer.android.com/kotlin/coroutines/coroutines-best-practices#mutable-types

sealed class PrivateStateFlow<T>(flow: StateFlow<T>) : StateFlow<T> by flow
sealed class PrivateSharedFlow<T>(flow: SharedFlow<T>) : SharedFlow<T> by flow

private class PrivateStateFlowImpl<T>(
    initial: T,
    val wrapped: MutableStateFlow<T> = MutableStateFlow(initial)
) : PrivateStateFlow<T>(wrapped)

private class PrivateSharedFlowImpl<T>(
    replay: Int,
    extraBufferCapacity: Int,
    onBufferOverflow: BufferOverflow,
    val wrapped: MutableSharedFlow<T> = MutableSharedFlow(replay, extraBufferCapacity, onBufferOverflow)
) : PrivateSharedFlow<T>(wrapped)

interface DataFlow {
    var <T> PrivateStateFlow<T>.mutable: T
        get() = when (this) {
            is PrivateStateFlowImpl -> wrapped.value
        }
        set(value) = when (this) {
            is PrivateStateFlowImpl -> wrapped.value = value
        }

    fun <T> privateStateFlow(initial: T): PrivateStateFlow<T> = PrivateStateFlowImpl(initial)

    suspend fun <T> PrivateStateFlow<T>.emit(value: T) = when (this) {
        is PrivateStateFlowImpl -> wrapped.emit(value)
    }

    fun <T> privateSharedFlow(
        replay: Int = 0,
        extraBufferCapacity: Int = 0,
        onBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND,
    ): PrivateSharedFlow<T> = PrivateSharedFlowImpl(replay, extraBufferCapacity, onBufferOverflow)

    suspend fun <T> PrivateSharedFlow<T>.emit(value: T) = when (this) {
        is PrivateSharedFlowImpl -> wrapped.emit(value)
    }

    fun <T> PrivateSharedFlow<T>.tryEmit(value: T) = when (this) {
        is PrivateSharedFlowImpl -> wrapped.tryEmit(value)
    }
}