import com.android.build.api.variant.HostTestBuilder

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

// MigrationTestHelper reads the exported schemas as assets, so the JVM migration tests get them.
androidComponents {
    onVariants { variant ->
        variant.hostTests[HostTestBuilder.UNIT_TEST_TYPE]
            ?.sources?.assets?.addStaticSourceDirectory("$projectDir/schemas")
    }
}

// Exported schemas are committed: they are the reference for migration tests (#9).
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:security"))
    // KhataDatabase extends RoomDatabase, so Room is part of this module's API.
    api(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite)

    // DAO and migration tests run on the JVM (Robolectric) so CI runs them.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.room.testing)
    androidTestImplementation(libs.room.testing)
}
