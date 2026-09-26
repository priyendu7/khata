pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Khata"

// Module layout from docs/DEVELOPMENT_PLAN.md; each module's README says what it owns.
include(":app")
include(":core:model", ":core:database", ":core:data", ":core:security", ":core:ui")
include(
    ":feature:transactions",
    ":feature:payees",
    ":feature:categories",
    ":feature:insights",
    ":feature:csv",
    ":feature:settings",
)
include(":sms:parser", ":sms:ingest")
