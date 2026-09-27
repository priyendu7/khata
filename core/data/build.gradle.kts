plugins {
    alias(libs.plugins.khata.android.library)
    alias(libs.plugins.khata.android.hilt)
}

android {
    namespace = "com.openhand.khata.core.data"
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:database"))
    implementation(libs.kotlinx.coroutines.core)

    // Repository tests run on the JVM with Robolectric and an in-memory database, so CI runs them.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
