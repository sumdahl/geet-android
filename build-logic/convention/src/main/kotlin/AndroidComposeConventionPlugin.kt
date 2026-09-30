import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

/** Applied on top of geet.android.library or geet.android.application. */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        extensions.getByType(CommonExtension::class.java).buildFeatures.compose = true
        // Material 3 Expressive is the chosen design language (ADR 0005); its APIs are still annotated experimental.
        tasks.withType<KotlinCompilationTask<*>>().configureEach {
            compilerOptions.optIn.addAll(
                "androidx.compose.material3.ExperimentalMaterial3Api",
                "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            )
        }
        dependencies {
            val bom = platform(lib("compose-bom"))
            add("implementation", bom)
            add("implementation", lib("compose-ui"))
            add("implementation", lib("compose-material3"))
            add("implementation", lib("compose-material-icons"))
            add("implementation", lib("compose-ui-tooling-preview"))
            add("debugImplementation", lib("compose-ui-tooling"))
        }
    }
}
