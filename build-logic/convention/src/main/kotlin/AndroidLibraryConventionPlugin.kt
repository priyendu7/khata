import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** Android library module: SDK levels, Java 17, lint and ktlint shared with :app. */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            pluginManager.apply("org.jetbrains.kotlin.android")
            pluginManager.apply("org.jlleitschuh.gradle.ktlint")

            extensions.configure<LibraryExtension> {
                compileSdk = libs.version("compileSdk").toInt()
                defaultConfig.minSdk = libs.version("minSdk").toInt()
                compileOptions {
                    sourceCompatibility = KHATA_JAVA_VERSION
                    targetCompatibility = KHATA_JAVA_VERSION
                }
                lint {
                    warningsAsErrors = false
                    abortOnError = true
                }
            }
            configureKotlinJvmTarget()

            dependencies {
                add("testImplementation", libs.library("junit"))
            }
        }
    }
}
