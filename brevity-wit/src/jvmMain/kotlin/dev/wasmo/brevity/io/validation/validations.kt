package dev.wasmo.brevity.io.validation

import dev.wasmo.brevity.Issue
import dev.wasmo.brevity.Location
import dev.wasmo.brevity.PackageName
import dev.wasmo.brevity.ServiceName
import dev.wasmo.brevity.WitCompoundException
import dev.wasmo.brevity.WitException
import dev.wasmo.brevity.io.IoInlinePackage
import dev.wasmo.brevity.io.IoInterface
import dev.wasmo.brevity.io.IoService
import dev.wasmo.brevity.io.IoTopLevelUse
import dev.wasmo.brevity.io.IoToplevelWitPackage
import dev.wasmo.brevity.io.IoWitFile
import dev.wasmo.brevity.io.IoWitPackage
import dev.wasmo.brevity.io.IoWorld
import okio.Path

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
    Issue(
      "Duplicate definitions of $packageName",
      packageRefs.map { packageRef -> packageRef.location }.toList(),
    )
  }.map(::WitException)

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

fun validateUniqueServiceNames(toplevelPackages: List<IoToplevelWitPackage>): Map<ServiceName, IoService> {
  val services = mutableMapOf<ServiceName, MutableList<IoService>>()

  fun addService(serviceName: ServiceName, service: IoService) {
    services.getOrPut(serviceName) { mutableListOf() }.add(service)
  }

  for (pkg in toplevelPackages) {
    for (file in pkg.files) {
      for (item in file.items) {
        when (item) {
          is IoInlinePackage -> processInlinePackage(item, ::addService)
          is IoInterface, is IoWorld -> addService(
            ServiceName(pkg.packageName, item.name),
            item,
          )
          is IoTopLevelUse -> {}
        }
      }
    }
  }
  val collisions = mutableMapOf<ServiceName, List<IoService>>()
  val output = mutableMapOf<ServiceName, IoService>()

  for ((serviceName, serviceList) in services) {
    when (serviceList.size) {
      0 -> error("Invariant violated: service name exists without reference")
      1 -> output[serviceName] = serviceList.single()
      else -> {
        output[serviceName] = serviceList.first()
        collisions[serviceName] = serviceList
      }
    }
  }

  val collisionExceptions = collisions.map { (serviceName, serviceRefs) ->
    val locations = serviceRefs.map { serviceRef -> serviceRef.location }
    WitException(Issue("Duplicate definitions of $serviceName", locations))
  }

  when (collisionExceptions.size) {
    0 -> {}
    1 -> throw collisionExceptions.single()
    else -> throw WitCompoundException(collisionExceptions)
  }

  return output
}

private fun processInlinePackage(
  pkg: IoInlinePackage,
  addService: (ServiceName, IoService) -> Unit
) {
  for (decl in pkg.declarations) {
    when (decl) {
      is IoInlinePackage -> processInlinePackage(decl, addService)
      is IoInterface, is IoWorld -> addService(
        ServiceName(pkg.packageName, decl.name),
        decl,
      )
      is IoTopLevelUse -> {}
    }
  }
}

data class ServiceRef(
  val path: Path,
  val service: IoService,
)
