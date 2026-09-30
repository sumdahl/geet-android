plugins {
    `kotlin-dsl`
}

java { toolchain { languageVersion = JavaLanguageVersion.of(17) } }

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.hilt.gradlePlugin)
    compileOnly(libs.roborazzi.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "geet.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "geet.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "geet.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("hilt") {
            id = "geet.hilt"
            implementationClass = "HiltConventionPlugin"
        }
        register("screenshot") {
            id = "geet.screenshot"
            implementationClass = "ScreenshotConventionPlugin"
        }
        register("jvmLibrary") {
            id = "geet.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
    }
}
