plugins {
    id("android-library")
    id("publish")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.vinicius.sak.view"
    compileSdk = 36
    buildFeatures {
        compose = true
    }
}

version = "0.1.0"

publishing.publications.named<MavenPublication>("release") {
    artifactId = "sak-view"
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    api(composeBom)
    api(libs.compose.ui)
    api(libs.compose.material3)
    implementation(libs.compose.tooling.preview)
    debugImplementation(libs.compose.tooling)
}
