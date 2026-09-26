plugins {
    alias(libs.plugins.khata.android.library)
    alias(libs.plugins.khata.android.compose)
}

android {
    namespace = "com.openhand.khata.feature.insights"
}

dependencies {
    implementation(project(":core:ui"))
}
