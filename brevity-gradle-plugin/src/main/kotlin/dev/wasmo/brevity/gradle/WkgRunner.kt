package dev.wasmo.brevity.gradle

import java.io.File
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.AbstractExecTask
import org.gradle.process.ExecSpec

class WkgRunner(
  private val config: RegularFileProperty,
  private val workingDir: Provider<Directory>?,
) {
  fun exec(
    execSpec: ExecSpec,
    vararg args: String,
    builderAction: MutableList<String>.() -> Unit = {},
    ) {
    if (workingDir != null) { execSpec.workingDir(workingDir) }

    execSpec.commandLine(*buildInvocation(args, builderAction))
  }

  fun exec(
    task: AbstractExecTask<*>,
    vararg args: String,
    builderAction: MutableList<String>.() -> Unit = {},
  ) {
    if (workingDir != null) { task.workingDir(workingDir) }

    task.commandLine(*buildInvocation(args, builderAction))
  }

  private fun buildInvocation(
    args: Array<out String>,
    builderAction: MutableList<String>.() -> Unit,
  ): Array<String> = buildList {
    add(probe("wkg"))

    args.forEach(::add)

    if (config.isPresent) {
      add("--config")
      add(config.get().asFile.absolutePath)
    }
    builderAction()
  }.toTypedArray()

  /**
   * For some inexplicable reason Java’s PATH isn’t resolving
   * certain Rust tools, so we do that manually.
   */
  private fun probe(executableName: String): String = System.getenv("PATH")
    .split(File.pathSeparator)
    .map { File(it, executableName) }
    .firstOrNull { it.canExecute() }
    ?.absolutePath
    ?: executableName

}
