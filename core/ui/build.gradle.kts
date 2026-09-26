plugins {
    alias(libs.plugins.khata.android.library)
    alias(libs.plugins.khata.android.compose)
}

android {
    namespace = "com.openhand.khata.core.ui"
}

dependencies {
    implementation(project(":core:model"))
}
