package dev.wasmo.brevity.gradle

import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
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
    execOperations.javaexec {
      classpath(this@BrevityTask.classpath)
      mainClass.set("dev.wasmo.brevity.cli.BrevityCommandKt")
      args = buildList {
        add("generate-kotlin")
        for (file in inputWitPackageDirectories) {
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
      }
    }
  }
}
