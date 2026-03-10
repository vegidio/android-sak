plugins {
    id("android-library")
    id("publish")
}

android {
    namespace = "io.vinicius.sak.rest"
    compileSdk = 36
}

version = "0.1.0"

publishing.publications.named<MavenPublication>("release") {
    artifactId = "sak-rest"
}

dependencies {
    api(libs.retrofit.core)
    api(libs.retrofit.converter.gson)
    api(libs.okhttp.core)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.coroutines)
}
