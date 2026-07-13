package dev.wasmo.brevity.io.validation

import dev.wasmo.brevity.Offset
import dev.wasmo.brevity.PackageName
import dev.wasmo.brevity.WitCompoundException
import dev.wasmo.brevity.WitMultiplySitedException
import dev.wasmo.brevity.WitMultiplySitedException.Location
import dev.wasmo.brevity.io.IoInlinePackage
import dev.wasmo.brevity.io.IoToplevelWitPackage
import dev.wasmo.brevity.io.IoWitFile
import dev.wasmo.brevity.io.IoWitPackage
import okio.Path
import okio.Path.Companion.toPath

fun validateUniquePackageNames(toplevelPackages: List<IoToplevelWitPackage>): Map<PackageName, IoWitPackage> {
  val packageRefs = mutableMapOf<PackageName, MutableList<PackageRef>>()

  fun addPackage(pkgName: PackageName, pkg: PackageRef) {
    packageRefs.getOrPut(pkgName) { mutableListOf() }.add(pkg)
  }
  for (pkg in toplevelPackages) {
    addPackage(pkg.packageName, PackageRef.Directory(pkg))

    for ((path, file) in pkg.files) {
      for (inlinePkg in file.items.filterIsInstance<IoInlinePackage>()) {
        addPackage(inlinePkg.packageName, PackageRef.Inline(
          path, file, inlinePkg,
        ))
      }
    }
  }
  val collisions = mutableMapOf<PackageName, List<PackageRef>>()
  val output = mutableMapOf<PackageName, IoWitPackage>()

  for ((packageName, packageRefs) in packageRefs) {
    when (packageRefs.size) {
      0 -> error("Invariant violated: package name exists without reference")
      1 -> output[packageName] = packageRefs.single().pkg
      else -> {
        output[packageName] = packageRefs.first().pkg
        collisions[packageName] = packageRefs
      }
    }
  }

  val collisionExceptions = collisions.map { (packageName, packageRefs) ->
    WitMultiplySitedException("Duplicate definitions of $packageName", packageRefs.map { packageRef ->
      Location(packageRef.path.toString(), packageRef.offset)
    }.toList())
  }

  when (collisionExceptions.size) {
    0 -> {}
    1 -> throw collisionExceptions.single()
    else -> throw WitCompoundException(collisionExceptions)
  }

  return output
}

sealed interface PackageRef {
  val pkg: IoWitPackage
  val path: Path
  val offset: Offset

  data class Directory(override val pkg: IoToplevelWitPackage): PackageRef {
    private val firstPackageNameDef = pkg.files.firstNotNullOf { (path, file) ->
      if (file.packageName != null) {
        path
      } else {
        null
      }
    }

    override val path = firstPackageNameDef
    override val offset = Offset(0, 0)
  }
  data class Inline(override val path: Path, val file: IoWitFile, override val pkg: IoInlinePackage): PackageRef {
    override val offset = pkg.offset
  }
}

private val IoToplevelWitPackage.directory: Path
  get() = files.keys.first().parent ?: "".toPath()
