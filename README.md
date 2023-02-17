# Android Swiss Army Knife

Gradle library where I keep my custom classes, extensions and other files that help me during the development of my Android projects.

## 🧰 Toolbox

The Swiss Army Knife is currently divided in 2 packages:

### Network

* __FlowCallAdapterFactory:__ a Retrofit `CallAdapter` to output the responses as Flow.
* __GeneralConverterFactory:__ a general class to create different Retrofit `Converter`.
* __GraphqlFactory:__ to make GraphQL requests and process the responses.
* __NetworkState:__ enum with basic enum states and the ability to pass data to errors.
* __RestFactory:__ to make REST requests and process the responses.

### Util

* __DataFlow:__ an interface that allows the creation of `PrivateStateFlow` and `PrivateSharedFlow`.

### View

* __ListRow:__ a view to be used in lists with chevron and separator set by default.
* __MyEmailField:__ a `MyTextField` with e-mail parameters set by default.
* __MyPasswordField:__ a `MyTextField` with password parameters set by default.
* __MyTextField:__ an `OutlinedTextField` with a few extra features set by default.
* __OverlaidColumn:__ a `Column` with multiple overlays that change depending on a state.
* __OverlaidRow:__ a `Row` with multiple overlays that change depending on a state.

## 🎨 Code Correctness

This project uses [Ktlint](https://github.com/pinterest/ktlint) to keep the code formatted and [Detekt](https://github.com/detekt/detekt) to follow best practices. The linting is done by running the command below in the project's root dir:

```
$ ./gradlew ktlintFormat detekt
```

## 👷🏾‍♂️ Building

This library is published with the help of [Jitpack](https://jitpack.io/#vegidio/android-sak). In order to make the library work properly, there are a few conditions that need to be satisfied:

1. Each module should use the plugin `maven-publish` and add the publishing instruction below:

```kotlin
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("maven") {
                from(components["release"])
            }
        }
    }
}
```

2. If the package has only one module, the lib should be imported using `com.github.vegidio:android-sak:<version>`, but if there are 2 or more modules then the lib should be imported using `com.github.vegidio.android-sak:lib1:<version>`, `com.github.vegidio.android-sak:lib2:<version>`, etc.

3. The code in each module should __not__ be minified.

4. The `sourceCompatibility` and `targetCompatibility` must target at least Java 11 (`JavaVersion.VERSION_11`).

## 👨🏾‍💻 Author

Vinicius Egidio ([vinicius.io](http://vinicius.io))