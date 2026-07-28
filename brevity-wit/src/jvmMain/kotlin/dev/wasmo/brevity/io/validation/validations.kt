package dev.wasmo.brevity.io.validation

import dev.wasmo.brevity.Location
import dev.wasmo.brevity.PackageName
import dev.wasmo.brevity.WitCompoundException
import dev.wasmo.brevity.WitMultiplySitedException
import dev.wasmo.brevity.io.IoInlinePackage
import dev.wasmo.brevity.io.IoToplevelWitPackage
import dev.wasmo.brevity.io.IoWitFile
import dev.wasmo.brevity.io.IoWitPackage

fun validateUniquePackageNames(
  toplevelPackages: List<IoToplevelWitPackage>,
): Map<PackageName, IoWitPackage> {
  val packageRefs = mutableMapOf<PackageName, MutableList<PackageRef>>()

  fun addPackage(packageName: PackageName, packageRef: PackageRef) {
    packageRefs.getOrPut(packageName) { mutableListOf() }.add(packageRef)
  }
  for (topLevelPackage in toplevelPackages) {
    addPackage(topLevelPackage.packageName, PackageRef.Directory(topLevelPackage))

    for (file in topLevelPackage.files) {
      for (inlinePackage in file.items.filterIsInstance<IoInlinePackage>()) {
        addPackage(
          inlinePackage.packageName,
          PackageRef.Inline(file, inlinePackage),
        )
      }
    }
  }
  val collisions = mutableMapOf<PackageName, List<PackageRef>>()
  val output = mutableMapOf<PackageName, IoWitPackage>()

  for ((packageName, packageRefs) in packageRefs) {
    when (packageRefs.size) {
      0 -> error("Invariant violated: package name exists without reference")
      1 -> output[packageName] = packageRefs.single().`package`
      else -> {
        output[packageName] = packageRefs.first().`package`
        collisions[packageName] = packageRefs
      }
    }
  }

  val collisionExceptions = collisions.map { (packageName, packageRefs) ->
    WitMultiplySitedException(
      "Duplicate definitions of $packageName",
      packageRefs.map { packageRef ->
        packageRef.location
      }.toList(),
    )
  }

  when (collisionExceptions.size) {
    0 -> {}
    1 -> throw collisionExceptions.single()
    else -> throw WitCompoundException(collisionExceptions)
  }

  return output
}

sealed interface PackageRef {
  val `package`: IoWitPackage
  val location: Location

  data class Directory(
    override val `package`: IoToplevelWitPackage,
  ) : PackageRef {
    override val location = `package`.files.firstNotNullOf { file ->
      file.location.takeIf { file.packageName != null }
    }
  }

  data class Inline(
    val file: IoWitFile,
    override val `package`: IoInlinePackage,
  ) : PackageRef {
    override val location: Location
      get() = `package`.location
  }
}
