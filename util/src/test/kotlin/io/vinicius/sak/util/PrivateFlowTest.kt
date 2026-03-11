@file:OptIn(ExperimentalCoroutinesApi::class)

package io.vinicius.sak.util

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateFlowTest {

    // Helper class that implements PrivateFlow, simulating a ViewModel
    private class FakeViewModel : PrivateFlow {
        val counter = privateStateFlow(0)
        val name = privateStateFlow("initial")
        val events = privateSharedFlow<String>(replay = 1)
        val bufferedEvents =
            privateSharedFlow<String>(
                replay = 0,
                extraBufferCapacity = 1,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )

        fun incrementCounter() {
            counter.set(counter.value + 1)
        }

        fun setName(value: String) {
            name.set(value)
        }

        suspend fun sendEvent(value: String) {
            events.emit(value)
        }

        fun trySendEvent(value: String): Boolean {
            return events.tryEmit(value)
        }

        suspend fun sendBufferedEvent(value: String) {
            bufferedEvents.emit(value)
        }

        fun trySendBufferedEvent(value: String): Boolean {
            return bufferedEvents.tryEmit(value)
        }
    }

    // region PrivateStateFlow

    @Test
    fun `privateStateFlow has correct initial value`() {
        val vm = FakeViewModel()
        assertEquals(0, vm.counter.value)
        assertEquals("initial", vm.name.value)
    }

    @Test
    fun `set updates state flow value`() {
        val vm = FakeViewModel()
        vm.incrementCounter()
        assertEquals(1, vm.counter.value)
    }

    @Test
    fun `set can be called multiple times`() {
        val vm = FakeViewModel()
        repeat(5) { vm.incrementCounter() }
        assertEquals(5, vm.counter.value)
    }

    @Test
    fun `set works with different types`() {
        val vm = FakeViewModel()
        vm.setName("updated")
        assertEquals("updated", vm.name.value)
    }

    @Test
    fun `privateStateFlow is a StateFlow`() {
        val vm = FakeViewModel()
        assertTrue(vm.counter is StateFlow<*>)
    }

    @Test
    fun `privateStateFlow is a PrivateStateFlow`() {
        val vm = FakeViewModel()
        assertTrue(vm.counter is PrivateStateFlow<*>)
    }

    @Test
    fun `state flow collectors receive updates`() = runTest {
        val vm = FakeViewModel()
        vm.counter.test {
            assertEquals(0, awaitItem()) // initial value
            vm.incrementCounter()
            assertEquals(1, awaitItem())
            vm.incrementCounter()
            assertEquals(2, awaitItem())
            vm.incrementCounter()
            assertEquals(3, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `state flow only emits distinct values`() = runTest {
        val vm = FakeViewModel()
        vm.name.test {
            assertEquals("initial", awaitItem())
            vm.setName("a")
            assertEquals("a", awaitItem())
            vm.setName("a") // duplicate, should be conflated
            vm.setName("b")
            assertEquals("b", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `multiple state flows are independent`() {
        val vm = FakeViewModel()
        vm.incrementCounter()
        vm.setName("changed")

        assertEquals(1, vm.counter.value)
        assertEquals("changed", vm.name.value)
    }

    // endregion

    // region PrivateSharedFlow

    @Test
    fun `privateSharedFlow is a SharedFlow`() {
        val vm = FakeViewModel()
        assertTrue(vm.events is SharedFlow<*>)
    }

    @Test
    fun `privateSharedFlow is a PrivateSharedFlow`() {
        val vm = FakeViewModel()
        assertTrue(vm.events is PrivateSharedFlow<*>)
    }

    @Test
    fun `shared flow emit delivers value to collector`() = runTest {
        val vm = FakeViewModel()
        vm.events.test {
            vm.sendEvent("hello")
            assertEquals("hello", awaitItem())
            vm.sendEvent("world")
            assertEquals("world", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `shared flow tryEmit returns true when buffer available`() {
        val vm = FakeViewModel()
        // events has replay=1, so tryEmit should succeed
        val result = vm.trySendEvent("test")
        assertTrue(result)
    }

    @Test
    fun `shared flow replay delivers last value to new collector`() = runTest {
        val vm = FakeViewModel()

        // Emit before any collector subscribes
        vm.sendEvent("replayed")

        // New collector should get the replayed value
        vm.events.test {
            assertEquals("replayed", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `shared flow with DROP_OLDEST does not suspend`() = runTest {
        val vm = FakeViewModel()
        // bufferedEvents has extraBufferCapacity=1, onBufferOverflow=DROP_OLDEST
        val result = vm.trySendBufferedEvent("event1")
        assertTrue(result)
    }

    @Test
    fun `shared flow with no replay does not emit to late collectors`() = runTest {
        val vm = FakeViewModel()

        // bufferedEvents has replay=0
        vm.sendBufferedEvent("missed")

        vm.bufferedEvents.test {
            // "missed" was emitted before collector, with replay=0 it should not be received
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `shared flow does not conflate duplicate values`() = runTest {
        val vm = FakeViewModel()
        vm.events.test {
            vm.sendEvent("same")
            assertEquals("same", awaitItem())
            vm.sendEvent("same")
            assertEquals("same", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // endregion

    // region External mutation is not possible without PrivateFlow

    @Test
    fun `privateStateFlow exposes read-only StateFlow interface`() {
        val vm = FakeViewModel()
        val flow: StateFlow<Int> = vm.counter
        // flow.value is readable
        assertEquals(0, flow.value)
        // There is no set(), emit(), or value setter accessible on StateFlow<Int>
    }

    @Test
    fun `privateSharedFlow exposes read-only SharedFlow interface`() {
        val vm = FakeViewModel()
        val flow: SharedFlow<String> = vm.events
        // The reference is typed as SharedFlow, not MutableSharedFlow
        assertTrue(flow is SharedFlow<*>)
    }

    // endregion
}
