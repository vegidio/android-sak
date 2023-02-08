
// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version Versions.android apply false
    id("com.android.library") version Versions.android apply false
    id("org.jetbrains.kotlin.android") version Versions.kotlin_android apply false
    id("io.gitlab.arturbosch.detekt") version Versions.detekt
    id("org.jlleitschuh.gradle.ktlint") version Versions.ktlint
}

buildscript {
    dependencies {
        classpath(Deps.android_gradle)
    }
}

detekt {
    source = files("$rootDir/network/src/main/kotlin")
    config = files("$rootDir/config/detekt.yml")
}

ktlint {
    android.set(true)
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
}