import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Hilt dependency injection (compile-time code generation through KSP; no reflection, no network).
 * The Hilt and KSP plugins are applied by ID from the root build's classpath; build-logic doesn't
 * compile against them (KSP's metadata is newer than Gradle's embedded Kotlin can read).
 */
class AndroidHiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")
            pluginManager.apply("com.google.dagger.hilt.android")

            dependencies {
                add("implementation", libs.library("hilt-android"))
                add("ksp", libs.library("hilt-compiler"))
                // Hilt 2.58 bundles a Kotlin metadata reader that stops at 2.3; drop once Hilt is
                // upgraded with AGP 9 (issue #16).
                add("ksp", libs.library("kotlin-metadata-jvm"))
            }
        }
    }
}
