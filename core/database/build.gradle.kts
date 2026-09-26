plugins {
    alias(libs.plugins.khata.android.library)
    alias(libs.plugins.khata.android.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "com.openhand.khata.core.database"
}

// Exported schemas are committed: they are the reference for migration tests (#9).
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":core:security"))
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite)
}
