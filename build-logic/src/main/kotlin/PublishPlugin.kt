import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register

class PublishPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("maven-publish")

            // Android libraries must opt the release variant into publishing.
            pluginManager.withPlugin("com.android.library") {
                extensions.configure<LibraryExtension> {
                    publishing {
                        singleVariant("release")
                    }
                }
            }

            // Plain Kotlin/JVM modules (e.g. the KSP compiler) publish sources alongside the jar.
            pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
                extensions.configure<JavaPluginExtension> {
                    withSourcesJar()
                }
            }

            extensions.configure<PublishingExtension> {
                repositories {
                    maven {
                        name = "GitHubPackages"
                        url = uri("https://maven.pkg.github.com/vegidio/android-sak")
                        credentials {
                            username = System.getenv("GITHUB_ACTOR")
                            password = System.getenv("GITHUB_TOKEN")
                        }
                    }
                }

                publications {
                    register<MavenPublication>("release") {
                        groupId = "io.vinicius.sak"
                        version = System.getenv("VERSION") ?: "0.0.0"
                        // artifactId is set per-module in its build.gradle.kts
                    }
                }
            }

            // Wire the software component once it exists: AGP's "release" for Android libraries,
            // or the "java" component for plain Kotlin/JVM modules.
            afterEvaluate {
                extensions.configure<PublishingExtension> {
                    publications.named("release", MavenPublication::class.java) {
                        val componentName = if (components.findByName("release") != null) "release" else "java"
                        from(components.getByName(componentName))
                    }
                }
            }
        }
    }
}
