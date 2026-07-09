package dev.wasmo.brevity.io.validation

import dev.wasmo.brevity.PackageName
import dev.wasmo.brevity.WitException
import dev.wasmo.brevity.io.IoInlinePackage
import dev.wasmo.brevity.io.IoToplevelWitPackage
import dev.wasmo.brevity.io.IoWitPackage

fun validateUniquePackageNames(toplevelPackages: List<IoToplevelWitPackage>): Map<PackageName, IoWitPackage> {
  val collisions = mutableMapOf<PackageName, MutableList<IoWitPackage>>()
  return buildMap {
    val inlinePackages = toplevelPackages.flatMap { toplevel ->
      toplevel.files.values.flatMap { file ->
        file.items.filterIsInstance<IoInlinePackage>()
      }
    }
    for (pkg in toplevelPackages + inlinePackages) {
      val existing = this[pkg.packageName]
      if (existing != null) {
        val collisionsList = collisions[pkg.packageName] ?: mutableListOf(existing).also {
          collisions[pkg.packageName] = it
        }

        collisionsList.add(pkg)
      } else {
        this[pkg.packageName] = pkg
      }
    }
    // Just blow up on the first collision for now.
    collisions.entries.firstOrNull()?.let { (packageName, _) ->
      throw WitException(
        "Duplicate package definitions for $packageName",
      )
    }
  }
}
