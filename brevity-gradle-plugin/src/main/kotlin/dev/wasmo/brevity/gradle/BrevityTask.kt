package dev.wasmo.brevity.gradle

import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

@CacheableTask
abstract class BrevityTask : DefaultTask() {
  @get:Inject
  abstract val execOperations: ExecOperations

  @get:Classpath
  abstract val classpath: ConfigurableFileCollection

  /** Each directory should contain a single .wit package. */
  @get:InputFiles
  @get:SkipWhenEmpty
  @get:IgnoreEmptyDirectories
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val inputWitPackageDirectories: ConfigurableFileCollection

  /** World names like 'command', 'wasi:cli/command', or 'wasi:cli/command@0.3.0' */
  @get:Input
  abstract val worlds: ListProperty<String>

  /**
   * Maps WIT types to Kotlin types. The two type names must be fully-qualified like
   * `wasi:clocks/types.duration@0.3.1` to `kotin.time.Duration`.
   */
  @get:Input
  abstract val customTypeMappings: MapProperty<String, String>

  @get:OutputDirectory
  internal abstract val outputKotlinCommonMain: DirectoryProperty

  @get:OutputDirectory
  internal abstract val outputKotlinWasmWasiMain: DirectoryProperty

  @get:OutputDirectory
  internal abstract val outputKotlinJvmMain: DirectoryProperty

  init {
    group = "brevity"
    description = "generate Kotlin from WIT"
  }

  @TaskAction
  fun execute() {
    val witPackageDirectories = buildList {
      collectWitDirectoriesRecursively(inputWitPackageDirectories.toList())
    }

    execOperations.javaexec {
      classpath(this@BrevityTask.classpath)
      mainClass.set("dev.wasmo.brevity.cli.BrevityCommandKt")
      args = buildList {
        add("generate-kotlin")
        for (file in witPackageDirectories) {
          add("--wit")
          add(file.path)
        }
        add("--commonMain")
        add(outputKotlinCommonMain.get().asFile.path)
        add("--wasmWasiMain")
        add(outputKotlinWasmWasiMain.get().asFile.path)
        add("--jvmMain")
        add(outputKotlinJvmMain.get().asFile.path)
        for (world in worlds.get()) {
          add("--world")
          add(world)
        }
        for ((key, value) in customTypeMappings.get()) {
          add("--type")
          add("$key=$value")
        }
      }
    }
  }

  /**
   * Recursively traverse the raw input [directories] looking for directories that contain `.wit`
   * files. If a directory contains at least one `.wit` file it is considered to be a package
   * directory and added to the inputs. Otherwise, its child directories are recursively visited.
   */
  private fun MutableList<File>.collectWitDirectoriesRecursively(directories: List<File>) {
    for (directory in directories) {
      val children = directory.listFiles() ?: continue
      if (children.any { it.name.endsWith(".wit", ignoreCase = true) }) {
        add(directory)
      } else {
        collectWitDirectoriesRecursively(children.toList())
      }
    }
  }
}
