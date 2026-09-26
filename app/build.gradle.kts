import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.khata.android.compose)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.play.publisher)
}

// Release signing is injected by CI (see .github/workflows/release.yml); local builds stay debug-only.
val releaseKeystorePath: String? = System.getenv("RELEASE_KEYSTORE_PATH")

android {
    namespace = "com.openhand.khata"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        // Overridable so contributors can publish a fork to their own Play account.
        applicationId = providers.gradleProperty("app.applicationId")
            .getOrElse("com.openhand.khata")
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        // CI derives these from the git tag (vX.Y.Z) and run number on release.
        versionCode = System.getenv("VERSION_CODE")?.toInt() ?: 1
        versionName = System.getenv("VERSION_NAME") ?: "0.1.0-dev"
        resValue("string", "app_name", "Khata")
    }

    signingConfigs {
        if (releaseKeystorePath != null) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
        // PR test builds for the separate "Khata QA" Play app (docs/TESTING_ON_PLAY.md).
        // Left unsigned: .github/workflows/play-test.yml signs it with the QA upload key.
        create("qa") {
            initWith(getByName("release"))
            applicationIdSuffix = ".qa"
            signingConfig = null
            matchingFallbacks += "release"
            resValue("string", "app_name", "Khata QA")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Google Play publishing (Gradle Play Publisher). CI supplies the service-account key;
// see docs/RELEASING.md (production app) and docs/TESTING_ON_PLAY.md (QA app).
play {
    serviceAccountCredentials.set(
        file(System.getenv("PLAY_SERVICE_ACCOUNT_JSON_PATH") ?: "play-service-account.json")
    )
    defaultToAppBundles.set(true)
    track.set("internal")
}

dependencies {
    // Compose (BOM, UI, Material 3) comes from the khata.android.compose convention plugin.
    implementation(libs.androidx.activity.compose)

    implementation(project(":core:ui"))
    implementation(project(":feature:transactions"))
    implementation(project(":feature:insights"))
    implementation(project(":feature:settings"))

    // Feature dependencies from docs/DEVELOPMENT_PLAN.md are added during feature work, in the PR that uses them.

    testImplementation(libs.junit)
}
