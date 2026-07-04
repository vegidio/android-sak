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
    compileOnly("com.android.tools.build:gradle:8.13.2")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
    compileOnly("dev.detekt:dev.detekt.gradle.plugin:2.0.0-alpha.5")
    compileOnly("org.jlleitschuh.gradle:ktlint-gradle:14.2.0")
}
