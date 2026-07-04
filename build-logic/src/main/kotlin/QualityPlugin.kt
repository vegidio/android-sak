import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jlleitschuh.gradle.ktlint.KtlintExtension

class QualityPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            // detekt is the linter (static analysis).
            pluginManager.apply("dev.detekt")

            // ktlint is used for formatting only (./gradlew ktlintFormat); linting stays with detekt.
            pluginManager.apply("org.jlleitschuh.gradle.ktlint")

            // Kover: code coverage. Auto-creates per-variant + total reports; aggregation is wired at root.
            pluginManager.apply("org.jetbrains.kotlinx.kover")

            extensions.configure<DetektExtension> {
                config.setFrom(rootProject.files("config/detekt.yml"))
                buildUponDefaultConfig.set(true)
                autoCorrect.set(false)
            }

            // Pin ktlint core to 1.8.0: it embeds a Kotlin 2.2+ frontend (so it parses Kotlin 2.3
            // syntax such as context parameters) and, unlike 1.7.0, runs cleanly under the
            // ktlint-gradle 14.2.0 worker (1.7.0 throws NoSuchMethodError on Gradle's bundled stdlib).
            // Style is driven by root .editorconfig (ktlint_official).
            extensions.configure<KtlintExtension> {
                version.set("1.8.0")
            }
        }
    }
}
