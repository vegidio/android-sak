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
    compileOnly("com.android.tools.build:gradle:8.9.2")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10")
}
