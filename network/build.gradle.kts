@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.config.JvmTarget

plugins {
    id(Plugins.android_lib)
    id(Plugins.kotlin)
    id(Plugins.maven)
}

android {
    namespace = "io.vinicius.sak.network"
    compileSdk = 33

    defaultConfig {
        minSdk = 30

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    sourceSets {
        // This is needed so Ktlint works properly
        configureEach {
            java.srcDir("src/$name/kotlin")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = JvmTarget.JVM_17.description
    }
}

dependencies {
    api(Deps.moshi)
    api(Deps.okhttp)
    api(Deps.okhttp_logging)
    api(Deps.retrofit)
    api(Deps.retrofit_moshi)

    implementation(Deps.apollo_runtime)
    implementation(Deps.core_ktx)
    implementation(Deps.coroutines_android)
    implementation(Deps.coroutines_core)

    testImplementation(Deps.junit)
    androidTestImplementation(Deps.android_junit)
    androidTestImplementation(Deps.android_espresso)
}

afterEvaluate {
    apply(from = "../publish.gradle.kts")
}