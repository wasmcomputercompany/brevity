package dev.wasmo.brevity.gradle

import java.io.File

object Paths {
  /**
   * For some inexplicable reason Java’s PATH isn’t resolving
   * certain Rust tools, so we do that manually.
   */
  fun probe(executableName: String): String = System.getenv("PATH")
    .split(File.pathSeparator)
    .map { File(it, executableName) }
    .firstOrNull { it.canExecute() }
    ?.absolutePath
    ?: executableName
}
