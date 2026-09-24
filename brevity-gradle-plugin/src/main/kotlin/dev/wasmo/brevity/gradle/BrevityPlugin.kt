package dev.wasmo.brevity.gradle

import java.io.File
import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Exec
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
class BrevityPlugin @Inject constructor(
  private val objects: ObjectFactory,
): Plugin<Project> {
  override fun apply(project: Project) {
    val wkgConfigProperty = objects.fileProperty()
    val witBuildSourceDir = project.layout.buildDirectory.dir("brevity/sourceWit")

    val wkg = WkgRunner(wkgConfigProperty, witBuildSourceDir)

    val copyWitSourceToBuildFolder = project.tasks.register<Copy>("copyWitSource") {
      from(File(project.projectDir, "src/commonMain/wit"))
      into(witBuildSourceDir)
    }

    val wkgFetchDependencies = project.tasks.register<Exec>("wkgFetchDependencies") {
      inputs.files(copyWitSourceToBuildFolder)
      outputs.files(
        project.layout.buildDirectory.files("brevity/sourceWit/wkg.lock"),
      )
      outputs.dirs(
        project.layout.buildDirectory.dir("brevity/sourceWit/wkg")
      )
      onlyIf { spec ->
        witBuildSourceDir.get().asFileTree.any { file -> file.name.endsWith(".wit") }
      }
      wkg.exec(this, "fetch")
    }

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
      dependsOn(wkgFetchDependencies)
      inputWitPackageDirectories.from(copyWitSourceToBuildFolder)
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
      RealBrevityExtension(wkgConfigProperty, downloadOciWitTask, generateKotlinTask) {
        val publishWitTask = project.tasks.register<PublishWitTask>("publishWit") {
          inputWkgWorkingDir.set(witBuildSourceDir)
          inputWkgConfig.set(wkgConfigProperty)
          dependsOn(copyWitSourceToBuildFolder)
        }
        RealBrevityPublishExtension(publishWitTask)
      },
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
  override val config: RegularFileProperty,
  private val downloadOciWitTask: TaskProvider<DownloadOciWitTask>,
  private val generateKotlinTask: TaskProvider<BrevityTask>,
  publishExtension: () -> BrevityExtension.BrevityPublishExtension,
) : BrevityExtension {
  override val ociPackages: ListProperty<String>
    get() = downloadOciWitTask.get().ociPackages
  override val worlds: ListProperty<String>
    get() = generateKotlinTask.get().worlds
  override val customTypeMappings: MapProperty<String, String>
    get() = generateKotlinTask.get().customTypeMappings

  private val publishExtension: BrevityExtension.BrevityPublishExtension by lazy {
    publishExtension()
  }

  override fun publish(action: Action<in BrevityExtension.BrevityPublishExtension>) {
    action.execute(publishExtension)
  }
}

internal class RealBrevityPublishExtension(
  private val publishWitTask: TaskProvider<PublishWitTask>,
): BrevityExtension.BrevityPublishExtension {
  override val isWorkspace: Property<Boolean>
    get() = publishWitTask.get().inputIsWorkspace

}
