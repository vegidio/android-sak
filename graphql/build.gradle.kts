plugins {
    id("android-library")
    id("publish")
    id("quality")
    id("com.apollographql.apollo") version "4.4.1"
}

android {
    namespace = "io.vinicius.sak.graphql"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
    }
}

ktfmt {
    kotlinLangStyle()
    maxWidth.set(120)
}

version = "0.1.0"

publishing.publications.named<MavenPublication>("release") {
    artifactId = "graphql"
}

apollo {
    service("service") {
        packageName.set("io.vinicius.sak.graphql.generated")
    }
}

dependencies {
    api(libs.apollo.runtime)
    implementation(libs.kotlinx.coroutines)
}
