// Top-level build file. Versions live in gradle/libs.versions.toml; shared module setup lives in
// build-logic/ (convention plugins khata.android.library, khata.android.compose, khata.jvm.library).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.play.publisher) apply false
}
