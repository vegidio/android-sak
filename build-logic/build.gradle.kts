plugins {
    `kotlin-dsl`
}

gradlePlugin {
    plugins {
        register("androidLibrary") {
            id = "android-library"
            implementationClass = "AndroidLibraryPlugin"
        }
        register("publish") {
            id = "publish"
            implementationClass = "PublishPlugin"
        }
    }
}

dependencies {
    compileOnly("com.android.tools.build:gradle:9.1.0")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10")
}
