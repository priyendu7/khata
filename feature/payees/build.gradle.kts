plugins {
    alias(libs.plugins.khata.android.library)
    alias(libs.plugins.khata.android.compose)
}

android {
    namespace = "com.openhand.khata.feature.payees"
}

dependencies {
    implementation(project(":core:ui"))
}
