plugins {
    alias(libs.plugins.khata.android.library)
    alias(libs.plugins.khata.android.compose)
    alias(libs.plugins.khata.android.hilt)
}

android {
    namespace = "com.openhand.khata.feature.insights"
    // Compose UI tests run on the JVM with Robolectric, so CI runs them on every PR.
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    // Asks for the notification permission when reminders are turned on.
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.core)
    // Due date reminders (#156), scheduled like the backup reminder.
    implementation(libs.androidx.work.runtime)

    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    // The ViewModel test runs on a real, in-memory database, like the repository tests.
    testImplementation(project(":core:database"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.work.testing)
    // Adds the empty activity that Compose UI tests host their content in (debug builds only).
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
