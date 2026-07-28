package dev.wasmo.brevity.io

import dev.wasmo.brevity.Documentation
import dev.wasmo.brevity.Location
import okio.FileSystem
import okio.Path

/**
 * Read all of the `.wit` files in a single directory, which collectively make up a package.
 *
 * A package name may be declared in any file in the directory; that name applies to all `.wit`
 * files in the same directory. If multiple files in the same directory declare a package, they must
 * declare the same package.
 */
class IoWitPackageReader(
  private val fileSystem: FileSystem,
) {
  fun read(directory: Path): IoToplevelWitPackage {
    val files = fileSystem.list(directory)
      .filter { it.name.endsWith(".wit", ignoreCase = true) }
      .map { path ->
        fileSystem.read(path) {
          val location = Location(path.relativeTo(directory))
          readUtf8().toWitFile(location)
        }
      }

    val packageNames = files.mapNotNull { it.packageName }.toSet()
    checkWit(packageNames.size == 1, location = Location(directory)) {
      when {
        packageNames.isEmpty() -> "no package declaration in directory"
        else -> {
          """
          |multiple different package names in directory:
          |  ${packageNames.sorted().joinToString(separator = "\n  ")}
          | """.trimMargin()
        }
      }
    }

    return IoToplevelWitPackage(
      documentation = files.mapNotNull { it.packageDocumentation }.concatenate(),
      packageName = packageNames.single(),
      files = files,
    )
  }

  private fun List<Documentation>.concatenate(): Documentation? {
    return when {
      isNotEmpty() -> Documentation(joinToString(separator = "\n") { it.content })
      else -> null
    }
  }
}
