package dev.wasmo.brevity.io.validation

import dev.wasmo.brevity.Offset
import dev.wasmo.brevity.PackageName
import dev.wasmo.brevity.ServiceName
import dev.wasmo.brevity.WitCompoundException
import dev.wasmo.brevity.WitMultiplySitedException
import dev.wasmo.brevity.WitMultiplySitedException.Location
import dev.wasmo.brevity.io.IoInlinePackage
import dev.wasmo.brevity.io.IoInterface
import dev.wasmo.brevity.io.IoService
import dev.wasmo.brevity.io.IoTopLevelUse
import dev.wasmo.brevity.io.IoToplevelWitPackage
import dev.wasmo.brevity.io.IoWitFile
import dev.wasmo.brevity.io.IoWitPackage
import dev.wasmo.brevity.io.IoWorld
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

fun validateUniqueServiceNames(toplevelPackages: List<IoToplevelWitPackage>): Map<ServiceName, IoService> {
  val serviceRefs = mutableMapOf<ServiceName, MutableList<ServiceRef>>()

  fun addService(serviceName: ServiceName, service: ServiceRef) {
    serviceRefs.getOrPut(serviceName) { mutableListOf() }.add(service)
  }
  for (pkg in toplevelPackages) {
    for ((path, file) in pkg.files) {
      for (item in file.items) {
        when (item) {
          is IoInlinePackage -> processInlinePackage(path, item, ::addService)
          is IoInterface, is IoWorld -> addService(
            ServiceName(pkg.packageName, item.name),
            ServiceRef(path, item),
          )
          is IoTopLevelUse -> {}
        }
      }
    }
  }
  val collisions = mutableMapOf<ServiceName, List<ServiceRef>>()
  val output = mutableMapOf<ServiceName, IoService>()

  for ((serviceName, serviceRefs) in serviceRefs) {
    when (serviceRefs.size) {
      0 -> error("Invariant violated: service name exists without reference")
      1 -> output[serviceName] = serviceRefs.single().service
      else -> {
        output[serviceName] = serviceRefs.first().service
        collisions[serviceName] = serviceRefs
      }
    }
  }

  val collisionExceptions = collisions.map { (serviceName, serviceRefs) ->
    WitMultiplySitedException("Duplicate definitions of $serviceName", serviceRefs.map { serviceRef ->
      Location(serviceRef.path.toString(), serviceRef.service.offset)
    }.toList())
  }

  when (collisionExceptions.size) {
    0 -> {}
    1 -> throw collisionExceptions.single()
    else -> throw WitCompoundException(collisionExceptions)
  }

  return output
}

private fun processInlinePackage(path: Path, pkg: IoInlinePackage, addService: (ServiceName, ServiceRef)->Unit) {
  for (decl in pkg.declarations) {
    when (decl) {
      is IoInlinePackage -> processInlinePackage(path, decl, addService)
      is IoInterface, is IoWorld -> addService(
        ServiceName(pkg.packageName, decl.name),
        ServiceRef(path, decl),
      )
      is IoTopLevelUse -> {}
    }
  }
}

data class ServiceRef(
  val path: Path,
  val service: IoService,
)

private val IoToplevelWitPackage.directory: Path
  get() = files.keys.first().parent ?: "".toPath()
