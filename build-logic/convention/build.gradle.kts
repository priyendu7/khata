plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.ktlint.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidLibrary") {
            id = "khata.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "khata.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidHilt") {
            id = "khata.android.hilt"
            implementationClass = "AndroidHiltConventionPlugin"
        }
        register("jvmLibrary") {
            id = "khata.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
    }
}
