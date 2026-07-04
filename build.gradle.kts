// Root build file — module configuration lives in build-logic convention plugins
plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.kover) // applied to root for aggregation
}

dependencies {
    kover(project(":rest"))
    kover(project(":rest-compiler"))
}
