import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** Pure Kotlin/JVM module with no Android dependencies (e.g. :core:model, :sms:parser). */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            pluginManager.apply("org.jlleitschuh.gradle.ktlint")

            extensions.configure<JavaPluginExtension> {
                sourceCompatibility = KHATA_JAVA_VERSION
                targetCompatibility = KHATA_JAVA_VERSION
            }
            configureKotlinJvmTarget()

            dependencies {
                add("testImplementation", libs.library("junit"))
            }

            // CI runs `testDebugUnitTest` across the build; alias it so JVM modules' tests run too.
            tasks.register("testDebugUnitTest") {
                group = "verification"
                description = "Runs this JVM module's unit tests (alias of `test`)."
                dependsOn("test")
            }
        }
    }
}
