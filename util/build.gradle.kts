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
    namespace = "io.vinicius.sak.util"
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
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.core)
    implementation(libs.usb.serial)

    testImplementation(libs.junit)
    androidTestImplementation(libs.test.espresso)
    androidTestImplementation(libs.test.ext)
}

afterEvaluate {
    apply(from = "../publish.gradle.kts")
}