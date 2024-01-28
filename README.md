# Android Swiss Army Knife

An Android library where I keep my custom classes, extensions and other files that help me during the development of my Android projects.

## ⬇️ Download

This library is hosted in my own Maven repository, so before using it in your project you must add the repository `https://maven.vinicius.io` to your `settings.gradle.kts` file:

```kotlin
dependencyResolutionManagement {
    //...
    repositories {
        google()
        mavenCentral()
        maven { setUrl("https://maven.vinicius.io") } // Add this line
    }
    //...
}
```

With the repository added, you just need to add the dependencies that you need to your project's `build.gradle.kts` file:

```kotlin
//...
dependencies {
    implementation("io.vinicius.sak:network:24.1.16") // Network SAK
    implementation("io.vinicius.sak:util:24.1.16")    // Util SAK
    implementation("io.vinicius.sak:view:24.1.16")    // View SAK
}
//...
```

## 🧰 Toolbox

The Swiss Army Knife is currently divided in 3 packages:

### Network

* __FlowCallAdapterFactory:__ a Retrofit `CallAdapter` to output the responses as Flow.
* __GeneralConverterFactory:__ a general class to create different Retrofit `Converter`s.
* __GraphqlFactory:__ to make GraphQL requests and process the responses.
* __RestFactory:__ to make REST requests and process the responses.
* __NetworkState:__ enum with basic states and the ability to pass data to errors.

#### Adapter

* __BigDecimalAdapter:__ a Moshi adapter to de/serialize `BigDecimal` fields.
* __LocalDateAdapter:__ a Moshi adapter to de/serialize `LocalDate` fields.
* __LocalTimeAdapter:__ a Moshi adapter to de/serialize `LocalTime` fields.
* __LocalDateTimeAdapter:__ a Moshi adapter to de/serialize `LocalDateTime` fields.

### Util

* __PrivateFlow:__ an interface that allows the creation of `PrivateStateFlow` and `PrivateSharedFlow`.
* __StringExtensions:__ a set of extensions useful for String manipulation.

### View

* __InputField:__ an `OutlinedTextField` with a few extra features set by default.
* __EmailField:__ a `InputField` with e-mail parameters set by default.
* __PasswordField:__ a `InputField` with password parameters set by default.
* __ListRow:__ a view to be used in lists with chevron and separator set by default.
* __Lottie:__ a view used to display Lottie animations.
* __OverlaidColumn:__ a `Column` with multiple overlays that change depending on a state.
* __OverlaidRow:__ a `Row` with multiple overlays that change depending on a state.
* __OverlaidLazyColumn:__ a `LazyColumn` with multiple overlays that change depending on a state.
* __OverlaidLazyRow:__ a `LazyRow` with multiple overlays that change depending on a state.
* __OverlaidLazyVerticalGrid:__ a `LazyVerticalGrid` with multiple overlays that change depending on a state.

## 🎨 Code Correctness

This project uses [Ktlint](https://github.com/pinterest/ktlint) to keep the code formatted and [Detekt](https://github.com/detekt/detekt) to follow best practices. The linting is done by running the command below in the project's root dir:

```
$ ./gradlew ktlintFormat detekt
```

## 👨🏾‍💻 Author

Vinicius Egidio ([vinicius.io](http://vinicius.io))