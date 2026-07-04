plugins {
    alias(libs.plugins.kotlin.jvm)
    id("publish")
    id("quality")
}

kotlin { jvmToolchain(17) }

version = "0.1.0"

publishing.publications.named<MavenPublication>("release") { artifactId = "rest-compiler" }

dependencies {
    implementation(libs.ksp.symbol.processing.api)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)
}