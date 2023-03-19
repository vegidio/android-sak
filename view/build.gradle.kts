@file:Suppress("UnstableApiUsage")

plugins {
    id(Plugins.android_lib)
    id(Plugins.kotlin)
    id(Plugins.maven)
}

android {
    namespace = "io.vinicius.sak.view"
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
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.4.0"
    }
}

dependencies {
    api(Deps.constraintlayout)
    api(Deps.lottie)

    implementation(Deps.compose_material3)
    implementation(Deps.compose_ui)
    implementation(Deps.compose_ui_tooling_preview)
    implementation(Deps.core_ktx)
    implementation(Deps.material_icons)

    debugImplementation(Deps.compose_ui_tooling)
    debugImplementation(Deps.compose_ui_test)
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