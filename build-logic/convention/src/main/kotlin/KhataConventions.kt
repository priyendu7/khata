import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.HasConfigurableKotlinCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.version(alias: String): String = findVersion(alias).get().requiredVersion

internal fun VersionCatalog.library(alias: String) = findLibrary(alias).get()

internal val KHATA_JAVA_VERSION = JavaVersion.VERSION_17

/** Every Kotlin module compiles to Java 17 bytecode. */
internal fun Project.configureKotlinJvmTarget() {
    @Suppress("UNCHECKED_CAST")
    (extensions.getByType<KotlinProjectExtension>() as HasConfigurableKotlinCompilerOptions<KotlinJvmCompilerOptions>)
        .compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
}
