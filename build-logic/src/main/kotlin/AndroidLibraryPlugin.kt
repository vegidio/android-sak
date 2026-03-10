import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Convention plugin that configures a module as an Android library.
 * Applies com.android.library and sets shared minSdk and JVM targets.
 *
 * Note: compileSdk and minSdk must be set in each module's own build.gradle.kts because
 * AGP does not reliably propagate them from convention plugins to all variants
 * (e.g. androidTest manifest generation).
 */
class AndroidLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")

            (extensions.getByName("android") as LibraryExtension).apply {
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

            // Disable androidTest variants — these library modules have no instrumented tests
            (extensions.getByName("androidComponents") as LibraryAndroidComponentsExtension).apply {
                beforeVariants { builder ->
                    builder.androidTest.enable = false
                }
            }
        }
    }
}
