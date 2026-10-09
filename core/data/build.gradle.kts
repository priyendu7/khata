plugins {
    alias(libs.plugins.khata.android.library)
    alias(libs.plugins.khata.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.openhand.khata.core.data"
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:database"))
    // The settings file (#125): its key comes from the same PBKDF2 step as the app PIN, and its
    // parser rules are checked the way Settings > Parsers checks them.
    implementation(project(":core:security"))
    implementation(project(":sms:parser"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    // Repository tests run on the JVM with Robolectric and an in-memory database, so CI runs them.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
