plugins {
    id("android-library")
    id("publish")
    id("quality")
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.vinicius.sak.rest"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
}

kotlin { jvmToolchain(17) }

ktfmt {
    kotlinLangStyle()
    maxWidth.set(120)
}

version = "0.1.0"

publishing.publications.named<MavenPublication>("release") { artifactId = "rest" }

dependencies {
    api(libs.retrofit.core)
    api(libs.retrofit.converter.kotlinx.serialization)
    api(libs.okhttp.core)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}
