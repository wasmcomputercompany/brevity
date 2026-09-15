package dev.wasmo.brevity.gradle

import java.io.File
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper

/**
 * Compile the `.wit` files in `src/commonMain/wit` and the [BrevityExtension.ociPackages] to
 * Kotlin, and add that Kotlin to this project.
 */
@Suppress("unused") // Registered as a Gradle plugin.
class BrevityPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val downloadOciWitTask = project.tasks.register<DownloadOciWitTask>("brevityDownloadOciWit") {
      witOutputDir.value(project.layout.buildDirectory.dir("brevity/oci-wit"))
    }

    val generateKotlinTask = project.tasks.register<BrevityTask>("brevityGenerateKotlin") {
      val cliConfiguration = try {
        project.configurations.create("cliConfiguration") {
          isCanBeResolved = true
        }.also { cliConfiguration ->
          project.dependencies {
            cliConfiguration(project.brevityDependency("brevity-cli"))
          }
        }
      } catch (_: InvalidUserDataException) {
        // This configuration already exists.
        project.configurations.named("cliConfiguration")
      }

      classpath.setFrom(cliConfiguration)
      inputWitPackageDirectories.from(File(project.projectDir, "src/commonMain/wit"))
      outputKotlinCommonMain.value(project.layout.buildDirectory.dir("brevity/commonMain"))
      outputKotlinWasmWasiMain.value(project.layout.buildDirectory.dir("brevity/wasmWasiMain"))
      outputKotlinJvmMain.value(project.layout.buildDirectory.dir("brevity/jvmMain"))
      inputWitPackageDirectories.from(project.tasks.withType<DownloadOciWitTask>())
    }

    // We need afterEvaluate {} because the Kotlin Multiplatform plugin is fragile to the order that
    // source sets are created:
    //   "The source set 'jvmMain' is already created
    //    and conflicts with the compilation 'main'."
    project.afterEvaluate {
      @OptIn(ExperimentalKotlinGradlePluginApi::class)
      project.plugins.withType<KotlinMultiplatformPluginWrapper> {
        val kotlin = project.extensions.getByName("kotlin") as KotlinMultiplatformExtension
        kotlin.apply {
          sourceSets.commonMain {
            generatedKotlin.srcDir(generateKotlinTask.map { it.outputKotlinCommonMain })
          }
          sourceSets.wasmWasiMain {
            generatedKotlin.srcDir(generateKotlinTask.map { it.outputKotlinWasmWasiMain })
          }
          sourceSets.jvmMain {
            generatedKotlin.srcDir(generateKotlinTask.map { it.outputKotlinJvmMain })
          }
        }
      }
    }

    project.extensions.add(
      BrevityExtension::class.java,
      "brevity",
      RealBrevityExtension(downloadOciWitTask, generateKotlinTask),
    )
  }

  /**
   * Returns either an internal project dependency (when running within Brevity's own build), or a
   * regular Maven dependency otherwise.
   */
  internal fun Project.brevityDependency(artifactId: String): Any {
    return when {
      extensions.findByName("brevityBuild") != null -> project(":$artifactId")
      else -> "dev.wasmo.brevity:$artifactId:$BREVITY_VERSION"
    }
  }
}

internal class RealBrevityExtension(
  private val downloadOciWitTask: TaskProvider<DownloadOciWitTask>,
  private val generateKotlinTask: TaskProvider<BrevityTask>,
) : BrevityExtension {
  override val ociPackages: ListProperty<String>
    get() = downloadOciWitTask.get().ociPackages
  override val worlds: ListProperty<String>
    get() = generateKotlinTask.get().worlds
  override val customTypeMappings: MapProperty<String, String>
    get() = generateKotlinTask.get().customTypeMappings
}
