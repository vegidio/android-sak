plugins {
    id("android-library")
    id("publish")
    id("quality")
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "io.vinicius.sak.util"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
}

kotlin {
    jvmToolchain(17)
    compilerOptions { optIn.add("kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi") }
}

ktfmt {
    kotlinLangStyle()
    maxWidth.set(120)
}

version = "0.1.0"

publishing.publications.named<MavenPublication>("release") { artifactId = "util" }

dependencies {
    api(libs.kotlinx.coroutines)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
