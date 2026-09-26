plugins {
    alias(libs.plugins.khata.android.library)
}

android {
    namespace = "com.openhand.khata.core.security"
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
}
