plugins {
    id("android-library")
    id("publish")
    id("quality")
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "io.vinicius.sak.rest"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }
}

kotlin { jvmToolchain(17) }

version = "0.1.0"

publishing.publications.named<MavenPublication>("release") { artifactId = "rest" }

dependencies {
    api(libs.retrofit.core)
    api(libs.retrofit.converter.kotlinx.serialization)
    api(libs.okhttp.core)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockk)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)

    // Generate @Service clients for the integration tests below.
    kspTest(project(":rest-compiler"))
}