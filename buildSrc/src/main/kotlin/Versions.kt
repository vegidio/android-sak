object Versions {
    const val android = "7.4.1"
    const val apollo = "3.7.4"
    const val compose_material3 = "1.1.0-alpha05"
    const val compose_ui = "1.3.3"
    const val core_ktx = "1.9.0"
    const val coroutines = "1.6.4"
    const val detekt = "1.22.0"
    const val kotlin = "1.8.0"
    const val ktlint = "11.1.0"
    const val moshi = "1.14.0"
    const val okhttp = "4.10.0"
    const val retrofit = "2.9.0"
}

object Plugins {
    const val android_app = "com.android.application"
    const val android_lib = "com.android.library"
    const val detekt = "io.gitlab.arturbosch.detekt"
    const val kotlin = "org.jetbrains.kotlin.android"
    const val ktlint = "org.jlleitschuh.gradle.ktlint"
    const val maven = "maven-publish"
}

object Deps {
    const val android_gradle = "com.android.tools.build:gradle:${Versions.android}"
    const val apollo_runtime = "com.apollographql.apollo3:apollo-runtime:${Versions.apollo}"
    const val compose_material3 = "androidx.compose.material3:material3:${Versions.compose_material3}"
    const val compose_ui = "androidx.compose.ui:ui:${Versions.compose_ui}"
    const val compose_ui_test = "androidx.compose.ui:ui-test-manifest:${Versions.compose_ui}"
    const val compose_ui_tooling = "androidx.compose.ui:ui-tooling:${Versions.compose_ui}"
    const val compose_ui_tooling_preview = "androidx.compose.ui:ui-tooling-preview:${Versions.compose_ui}"
    const val core_ktx = "androidx.core:core-ktx:${Versions.core_ktx}"
    const val coroutines_android = "org.jetbrains.kotlinx:kotlinx-coroutines-android:${Versions.coroutines}"
    const val coroutines_core = "org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutines}"
    const val moshi = "com.squareup.moshi:moshi-kotlin:${Versions.moshi}"
    const val okhttp = "com.squareup.okhttp3:okhttp:${Versions.okhttp}"
    const val okhttp_logging = "com.squareup.okhttp3:logging-interceptor:${Versions.okhttp}"
    const val retrofit = "com.squareup.retrofit2:retrofit:${Versions.retrofit}"
}