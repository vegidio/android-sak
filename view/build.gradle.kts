@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.config.JvmTarget

plugins {
    alias(libs.plugins.android.lib)
    alias(libs.plugins.kotlin)
    `maven-publish`

    // We must have the next plugins in all subprojects that need Ktlint
    alias(libs.plugins.ktlint)
}

android {
    namespace = "io.vinicius.sak.view"
    compileSdk = 34

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
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.7"
    }
}

dependencies {
    api(libs.constraintlayout)
    api(libs.lottie)

    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.core.ktx)
    implementation(libs.material.icons)

    debugImplementation(libs.compose.ui.test)
    debugImplementation(libs.compose.ui.tooling)
}

afterEvaluate {
    apply(from = "../publish.gradle.kts")
}