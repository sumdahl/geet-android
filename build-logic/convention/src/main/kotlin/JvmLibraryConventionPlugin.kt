import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        extensions.configure<JavaPluginExtension> {
            toolchain.languageVersion.set(JavaLanguageVersion.of(17))
        }
        dependencies {
            add("testImplementation", lib("junit"))
        }
        // Lets `./gradlew testDebugUnitTest` run pure-JVM tests alongside Android unit tests.
        tasks.register("testDebugUnitTest") { dependsOn("test") }
        Unit
    }
}
