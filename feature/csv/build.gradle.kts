plugins {
    alias(libs.plugins.khata.android.library)
    alias(libs.plugins.khata.android.compose)
}

android {
    namespace = "com.openhand.khata.feature.csv"
}

dependencies {
    implementation(project(":core:ui"))
}
