# Android Swiss Army Knife

Gradle library where I keep my custom classes, extensions and other files that help me during the development of my Android projects.

## 🧰 Toolbox

The Swiss Army Knife is currently divided in 2 packages:

### Network

* __RestFactory:__ to make HTTP requests and process the responses.

### View

* __MyTextField:__ an `OutlinedTextField` with a few extra features set by default.
* __MyEmailField:__ a `MyTextField` with e-mail parameters set by default.
* __MyPasswordField:__ a `MyTextField` with password parameters set by default.

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