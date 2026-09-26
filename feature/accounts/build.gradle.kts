plugins {
    alias(libs.plugins.khata.android.library)
    alias(libs.plugins.khata.android.compose)
    alias(libs.plugins.khata.android.hilt)
}

android {
    namespace = "com.openhand.khata.feature.accounts"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.core)
}
