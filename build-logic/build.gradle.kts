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
        register("quality") {
            id = "quality"
            implementationClass = "QualityPlugin"
        }
    }
}

dependencies {
    compileOnly("com.android.tools.build:gradle:8.9.2")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10")
    compileOnly("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.8")
}
