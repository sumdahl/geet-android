import com.android.build.api.variant.LibraryAndroidComponentsExtension

plugins {
    alias(libs.plugins.geet.android.library)
    alias(libs.plugins.geet.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.sumdahl.geet.engine"
}

// The engine is the geet CLI itself (github.com/sumdahl/geet), built for Android by its own
// scripts/build-android.sh and shipped as jniLibs/<abi>/libgeet.so: Android only runs executables
// that come as native libraries. -PgeetEngineDir points at a checkout (default: ../geet beside this repo).
val geetEngineDir = providers.gradleProperty("geetEngineDir")
    .orElse(rootProject.layout.projectDirectory.dir("../geet").asFile.absolutePath)

abstract class BuildGeetEngine : DefaultTask() {
    @get:Input abstract val engineDir: Property<String>

    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @get:Inject abstract val exec: ExecOperations

    @TaskAction
    fun build() {
        val src = File(engineDir.get())
        require(File(src, "scripts/build-android.sh").exists()) {
            "geet engine not found at $src: clone github.com/sumdahl/geet there or pass -PgeetEngineDir=…"
        }
        exec.exec {
            workingDir = src
            commandLine("sh", "scripts/build-android.sh")
        }
        val out = outputDir.get().asFile
        out.deleteRecursively()
        File(src, "build/android").copyRecursively(out)
    }
}

val buildGeetEngine = tasks.register<BuildGeetEngine>("buildGeetEngine") {
    engineDir.set(geetEngineDir)
    outputDir.set(layout.buildDirectory.dir("generated/geetEngine"))
    // Go builds are incremental and fast; always re-run so engine changes land.
    outputs.upToDateWhen { false }
}

extensions.configure<LibraryAndroidComponentsExtension> {
    onVariants { variant ->
        variant.sources.jniLibs?.addGeneratedSourceDirectory(buildGeetEngine, BuildGeetEngine::outputDir)
    }
}

dependencies {
    api(libs.youtubedl.library)
    api(libs.youtubedl.ffmpeg)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
}
