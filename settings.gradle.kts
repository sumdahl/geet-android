pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    // Provisions the JDK 21 that Robolectric screenshot tests need, on dev machines and CI alike.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "geet-android"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app", ":ui")
include(":core:engine", ":core:data", ":core:player", ":core:designsystem")
