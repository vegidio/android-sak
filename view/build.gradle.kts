plugins {
    id("android-library")
    id("publish")
    id("quality")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.vinicius.sak.view"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
    }
    buildFeatures {
        compose = true
    }
}

ktfmt {
    kotlinLangStyle()
    maxWidth.set(120)
}

version = "0.1.0"

publishing.publications.named<MavenPublication>("release") {
    artifactId = "view"
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    api(composeBom)
    api(libs.compose.ui)
    api(libs.compose.material3)
    implementation(libs.compose.tooling.preview)
    debugImplementation(libs.compose.tooling)
}
