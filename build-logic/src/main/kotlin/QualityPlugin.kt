import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class QualityPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("io.gitlab.arturbosch.detekt")
            pluginManager.apply("com.ncorti.ktfmt.gradle")

            extensions.configure<DetektExtension> {
                config.setFrom(rootProject.files("config/detekt.yml"))
                buildUponDefaultConfig = true
                autoCorrect = false
            }
        }
    }
}
