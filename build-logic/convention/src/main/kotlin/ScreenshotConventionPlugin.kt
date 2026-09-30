import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** Roborazzi screenshot tests on the JVM (Robolectric; JDK 21 set up by geet.android.library). Record: recordRoborazziDebug, check: verifyRoborazziDebug. */
class ScreenshotConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("io.github.takahirom.roborazzi")
        extensions.configure<LibraryExtension> {
            testOptions.unitTests.isIncludeAndroidResources = true
        }
        dependencies {
            add("testImplementation", lib("roborazzi"))
            add("testImplementation", lib("roborazzi-compose"))
            add("testImplementation", lib("compose-ui-test-junit4"))
            add("debugImplementation", lib("compose-ui-test-manifest"))
        }
    }
}
