import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register

class PublishPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("maven-publish")

            extensions.configure<LibraryExtension> {
                publishing {
                    singleVariant("release")
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

            // Wire the AGP "release" component after it has been created by AGP
            afterEvaluate {
                extensions.configure<PublishingExtension> {
                    publications.named("release", MavenPublication::class.java) {
                        from(components.getByName("release"))
                    }
                }
            }
        }
    }
}
