package dev.wasmo.brevity.io.validation

import dev.wasmo.brevity.Issue
import dev.wasmo.brevity.IssueCollector
import dev.wasmo.brevity.PackageName
import dev.wasmo.brevity.ServiceName
import dev.wasmo.brevity.io.IoInlinePackage
import dev.wasmo.brevity.io.IoInterface
import dev.wasmo.brevity.io.IoService
import dev.wasmo.brevity.io.IoTopLevelUse
import dev.wasmo.brevity.io.IoToplevelWitPackage
import dev.wasmo.brevity.io.IoWitPackage
import dev.wasmo.brevity.io.IoWorld
import okio.Path

context(issueCollector: IssueCollector)
fun validateUniquePackageNames(
  toplevelPackages: List<IoToplevelWitPackage>,
): Map<PackageName, IoWitPackage>? {
  val witPackageMap = mutableMapOf<PackageName, MutableList<IoWitPackage>>()

  fun addPackage(packageName: PackageName, witPackage: IoWitPackage) {
    witPackageMap.getOrPut(packageName) { mutableListOf() }.add(witPackage)
  }
  for (topLevelPackage in toplevelPackages) {
    addPackage(topLevelPackage.packageName, topLevelPackage)

    for (file in topLevelPackage.files) {
      for (inlinePackage in file.items.filterIsInstance<IoInlinePackage>()) {
        addPackage(
          inlinePackage.packageName,
          inlinePackage,
        )
      }
    }
  }
  val collisions = mutableMapOf<PackageName, List<IoWitPackage>>()
  val output = mutableMapOf<PackageName, IoWitPackage>()

  for ((packageName, witPackages) in witPackageMap) {
    when (witPackages.size) {
      0 -> error("Invariant violated: package name exists without reference")
      1 -> output[packageName] = witPackages.single()
      else -> {
        output[packageName] = witPackages.first()
        collisions[packageName] = witPackages
      }
    }
  }

  val issues = collisions.map { (packageName, packages) ->
    Issue(
      "Duplicate definitions of $packageName",
      packages.flatMap { witPackage ->
        when (witPackage) {
          is IoInlinePackage -> listOf(witPackage.location)
          is IoToplevelWitPackage -> witPackage.files.mapNotNull { witFile ->
            witFile.packageName?.location
          }
        }
      }.toList(),
    )
  }

  return if (issues.isEmpty()) {
    output
  } else {
    issues.forEach(issueCollector::report)
    null
  }
}

context(issueCollector: IssueCollector)
fun validateUniqueServiceNames(toplevelPackages: List<IoToplevelWitPackage>):
  Map<ServiceName, IoService>? {
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
  val issues = mutableListOf<Issue>()
  val output = mutableMapOf<ServiceName, IoService>()

  for ((serviceName, serviceList) in services) {
    when (serviceList.size) {
      0 -> error("Invariant violated: service name exists without reference")
      1 -> output[serviceName] = serviceList.single()
      else -> {
        output[serviceName] = serviceList.first()
        val locations = serviceList.map { it.location }
        val issue = Issue("Duplicate definitions of $serviceName", locations)
        issues += issue
        issueCollector.report(issue)
      }
    }
  }

  return if (issues.isEmpty()) {
    output
  } else {
    null
  }
}

private fun processInlinePackage(
  pkg: IoInlinePackage,
  addService: (ServiceName, IoService) -> Unit,
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
