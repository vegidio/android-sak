import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Convention plugin that configures a module as an Android library.
 * Applies com.android.library and sets shared minSdk and JVM targets.
 *
 * Note: compileSdk must be set in each module's own build.gradle.kts because
 * AGP 9.x validates that it is declared directly in the module's build file.
 */
class AndroidLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")

            (extensions.getByName("android") as LibraryExtension).apply {
                defaultConfig {
                    minSdk = 26
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }

                // Explicitly enable the release variant for publishing (required by AGP 9.x)
                publishing {
                    singleVariant("release") {
                        withSourcesJar()
                    }
                }
            }
        }
    }
}
