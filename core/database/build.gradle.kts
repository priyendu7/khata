plugins {
    alias(libs.plugins.khata.android.library)
    alias(libs.plugins.khata.android.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "com.openhand.khata.core.database"
    // Robolectric needs Android resources for Room DAO tests on the JVM.
    testOptions.unitTests.isIncludeAndroidResources = true
}

// Exported schemas are committed: they are the reference for migration tests (#9).
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:security"))
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite)

    // DAO tests run on the JVM (Robolectric) so CI runs them; migration tests run on a device.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.room.testing)
}
