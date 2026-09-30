plugins {
    alias(libs.plugins.khata.android.library)
    alias(libs.plugins.khata.android.compose)
    alias(libs.plugins.khata.android.hilt)
}

android {
    namespace = "com.openhand.khata.feature.settings"
    // Compose UI tests run on the JVM with Robolectric, so CI runs them on every PR.
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:model"))
    implementation(project(":core:ui"))
    implementation(project(":sms:ingest"))
    implementation(project(":sms:parser"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    // Adds the empty activity that Compose UI tests host their content in (debug builds only).
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
