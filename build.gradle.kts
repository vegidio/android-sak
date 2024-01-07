// Top-level build file where you can add configuration options common to all subprojects/modules
plugins {
    alias(libs.plugins.android.app) apply false
    alias(libs.plugins.android.lib) apply false
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

detekt {
    config.setFrom("$rootDir/config/detekt.yml")
    source.setFrom(
        "$rootDir/network/src/main/kotlin",
        "$rootDir/util/src/main/kotlin",
        "$rootDir/view/src/main/kotlin"
    )
}

ktlint {
    android.set(true)
}