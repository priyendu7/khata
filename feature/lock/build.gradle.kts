plugins {
    alias(libs.plugins.khata.android.library)
    alias(libs.plugins.khata.android.compose)
    alias(libs.plugins.khata.android.hilt)
}

android {
    namespace = "com.openhand.khata.feature.lock"
}

dependencies {
    implementation(project(":core:security"))
    implementation(project(":core:ui"))
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.core)
}
