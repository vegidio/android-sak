# util

A collection of utilities for Android development. This module currently provides `PrivateFlow`, a pattern for exposing immutable flows publicly while restricting mutation to the owning class.

## Quick start

Implement `PrivateFlow` in your ViewModel and declare a field with `privateStateFlow`:

```kotlin
class CountriesViewModel : ViewModel(), PrivateFlow {
    val state = privateStateFlow(CountriesContract.State())

    fun loadCountries() {
        state.set(CountriesContract.State(loading = true))
    }
}
```

Outside the ViewModel, `state` is a read-only `StateFlow<T>`. Inside the ViewModel, `.set()` and `.emit()` are available because `PrivateFlow` is implemented.

## PrivateFlow

`PrivateFlow` is an interface that replaces the conventional dual-declaration pattern:

```kotlin
// Before: two declarations for one piece of state
private val _state = MutableStateFlow(CountriesContract.State())
val state: StateFlow<CountriesContract.State> = _state.asStateFlow()
```

```kotlin
// After: one declaration with PrivateFlow
val state = privateStateFlow(CountriesContract.State())
```

### Mutating state

Use `.set()` to update the value synchronously:

```kotlin
class CountriesViewModel : ViewModel(), PrivateFlow {
    val state = privateStateFlow(CountriesContract.State())

    fun reset() {
        state.set(CountriesContract.State())
    }
}
```

### Emitting values

Use `.emit()` from a coroutine to update the value — equivalent to `MutableStateFlow.emit()`:

```kotlin
class CountriesViewModel : ViewModel(), PrivateFlow {
    val state = privateStateFlow(CountriesContract.State())

    fun loadCountries() {
        viewModelScope.launch {
            state.emit(CountriesContract.State(loading = true))
            val countries = repository.getCountries()
            state.emit(CountriesContract.State(countries = countries))
        }
    }
}
```

Use `.tryEmit()` for a non-suspending best-effort emit:

```kotlin
state.tryEmit(CountriesContract.State(error = true))
```

## PrivateSharedFlow

For event streams that should not replay state, use `privateSharedFlow`:

```kotlin
class CountriesViewModel : ViewModel(), PrivateFlow {
    val events = privateSharedFlow<CountriesContract.Event>()

    fun onCountrySelected(country: Country) {
        viewModelScope.launch {
            events.emit(CountriesContract.Event.NavigateToDetail(country))
        }
    }
}
```

### Buffer configuration

The underlying `MutableSharedFlow` buffer is fully configurable:

```kotlin
private val _events = privateSharedFlow<Event>(
    replay = 1,
    extraBufferCapacity = 10,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
)
```

| Parameter | Default | Description |
|-----------|---------|-------------|
| `replay` | `0` | Number of values replayed to new collectors |
| `extraBufferCapacity` | `0` | Additional buffer slots beyond replay |
| `onBufferOverflow` | `SUSPEND` | What to do when the buffer is full |

## PrivateChannel

For one-shot events that should not be replayed to new collectors, use `privateChannel`:

```kotlin
class SignInViewModel : ViewModel(), PrivateFlow {
    val effect = privateChannel<SignInContract.Effect>()

    fun onSignInSuccess() {
        viewModelScope.launch {
            effect.send(SignInContract.Effect.NavigateToHome)
        }
    }
}
```

Outside the ViewModel, `effect` is a read-only `Flow<T>`. Inside, `.send()` and `.trySend()` are available because `PrivateFlow` is implemented.

### Buffer configuration

```kotlin
val effect = privateChannel<Effect>(
    capacity = Channel.BUFFERED,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
    onUndeliveredElement = { /* handle undelivered */ },
)
```

| Parameter | Default | Description |
|-----------|---------|-------------|
| `capacity` | `Channel.BUFFERED` | Channel buffer capacity |
| `onBufferOverflow` | `SUSPEND` | What to do when the buffer is full |
| `onUndeliveredElement` | `null` | Called for elements that were never delivered |

## Encapsulation note

Mutation access is enforced by convention, not by the type system. Any class that implements `PrivateFlow` can call `.set()`, `.emit()`, and `.tryEmit()` on any `PrivateStateFlow` or `PrivateSharedFlow` instance it holds a reference to. Only implement `PrivateFlow` in classes that own the flow.
