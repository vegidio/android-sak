plugins {
    id("android-library")
    id("publish")
    id("com.apollographql.apollo") version "4.4.1"
}

android {
    namespace = "io.vinicius.sak.graphql"
    compileSdk = 36
}

version = "0.1.0"

publishing.publications.named<MavenPublication>("release") {
    artifactId = "sak-graphql"
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
