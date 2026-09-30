import java.util.Properties

plugins {
    alias(libs.plugins.geet.android.application)
    alias(libs.plugins.geet.android.compose)
    alias(libs.plugins.geet.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing: CI passes GEET_* env vars; locally an untracked keystore.properties works too.
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}
fun signingValue(env: String, prop: String): String? = providers.environmentVariable(env).orNull ?: keystoreProps.getProperty(prop)

android {
    namespace = "dev.sumdahl.geet"
    buildFeatures.buildConfig = true
    androidResources.localeFilters += listOf("en")
    packaging {
        // The engine, Python and ffmpeg are executables shipped as native libraries: Android runs them only from the
        // extracted native library directory (W^X since Android 10), so they must be extracted at install.
        jniLibs.useLegacyPackaging = true
        resources.excludes += listOf("META-INF/**/LICENSE*", "META-INF/*.version", "META-INF/*.kotlin_module", "DebugProbesKt.bin")
    }
    // One APK per ABI: Python, ffmpeg and the engine are ~60 MB per ABI, so a universal APK would be ~4x the download.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }
    signingConfigs {
        signingValue("GEET_KEYSTORE", "storeFile")?.let { path ->
            create("release") {
                storeFile = file(path)
                storePassword = signingValue("GEET_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("GEET_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("GEET_KEY_PASSWORD", "keyPassword")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    defaultConfig {
        applicationId = "dev.sumdahl.geet"
        versionCode = providers.environmentVariable("VERSION_CODE").map(String::toInt).getOrElse(1)
        versionName = providers.environmentVariable("VERSION_NAME").getOrElse("0.1.0")
    }
}

dependencies {
    implementation(projects.ui)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.splashscreen)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)
    implementation(libs.compose.material3.adaptive.suite)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.coil.compose)
    implementation(libs.media3.session)
}
