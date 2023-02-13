@file:Suppress("UnstableApiUsage")

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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(Deps.apollo_runtime)
    implementation(Deps.core_ktx)
    implementation(Deps.coroutines_android)
    implementation(Deps.coroutines_core)
    implementation(Deps.moshi)
    implementation(Deps.okhttp)
    implementation(Deps.okhttp_logging)
    implementation(Deps.retrofit)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("maven") {
                from(components["release"])
            }
        }
    }
}