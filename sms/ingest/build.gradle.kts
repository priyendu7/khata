plugins {
    alias(libs.plugins.khata.android.library)
    alias(libs.plugins.khata.android.hilt)
}

android {
    namespace = "com.openhand.khata.sms.ingest"
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    implementation(project(":sms:parser"))
    implementation(project(":core:data"))
    implementation(project(":core:model"))
    implementation(libs.androidx.work.runtime)
    implementation(libs.kotlinx.coroutines.core)

    // Tests run on the JVM with Robolectric, a fake SMS inbox and an in-memory database.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":core:database"))
}
