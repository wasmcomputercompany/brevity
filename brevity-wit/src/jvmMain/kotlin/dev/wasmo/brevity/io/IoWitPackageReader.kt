package dev.wasmo.brevity.io

import dev.wasmo.brevity.Documentation
import dev.wasmo.brevity.Issue
import dev.wasmo.brevity.WitException
import dev.wasmo.brevity.WitSyntaxException
import dev.wasmo.brevity.location
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
    val files = mutableMapOf<Path, IoWitFile>()
    for (path in fileSystem.list(directory)) {
      if (!path.name.endsWith(".wit", ignoreCase = true)) continue

      val relativePath = path.relativeTo(directory)
      try {
        files[relativePath] = fileSystem.read(path) {
          readUtf8().toWitFile()
        }
      } catch (e: WitSyntaxException) {
        throw WitException(
            Issue(
              description = e.description,
              location = path.location(e.offset),
            )
          )
      }
    }

    val packageNames = files.values.mapNotNull { it.packageName }.toSet()
    checkWit(packageNames.size == 1, path = "$directory") {
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
      documentation = files.values.mapNotNull { it.packageDocumentation }.concatenate(),
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
