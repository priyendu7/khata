import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * detekt static analysis with the shared config in `config/detekt/detekt.yml` and a per-module
 * baseline (`detekt-baseline.xml`) for findings that existed when detekt was added.
 * Run with `./gradlew detekt`; refresh a baseline with `./gradlew :<module>:detektBaseline`.
 */
internal fun Project.configureDetekt() {
    pluginManager.apply("io.gitlab.arturbosch.detekt")
    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        baseline = file("detekt-baseline.xml")
        source.setFrom("src/main/java", "src/main/kotlin", "src/test/java", "src/androidTest/java")
    }
}
