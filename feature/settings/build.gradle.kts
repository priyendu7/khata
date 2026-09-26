plugins {
    alias(libs.plugins.khata.android.library)
    alias(libs.plugins.khata.android.compose)
    alias(libs.plugins.khata.android.hilt)
}

android {
    namespace = "com.openhand.khata.feature.settings"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:ui"))
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
}
