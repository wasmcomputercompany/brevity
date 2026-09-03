package dev.wasmo.brevity.io.validation

import dev.wasmo.brevity.Issue
import dev.wasmo.brevity.IssueCollector
import dev.wasmo.brevity.IoPackageName
import dev.wasmo.brevity.IoServiceName
import dev.wasmo.brevity.ServiceName
import dev.wasmo.brevity.io.IoCase
import dev.wasmo.brevity.io.IoDeclaration
import dev.wasmo.brevity.io.IoEnum
import dev.wasmo.brevity.io.IoExternalApi
import dev.wasmo.brevity.io.IoField
import dev.wasmo.brevity.io.IoFlag
import dev.wasmo.brevity.io.IoFlags
import dev.wasmo.brevity.io.IoFunction
import dev.wasmo.brevity.io.IoInclude
import dev.wasmo.brevity.io.IoInlinePackage
import dev.wasmo.brevity.io.IoInterface
import dev.wasmo.brevity.io.IoNamedDeclaration
import dev.wasmo.brevity.io.IoParameter
import dev.wasmo.brevity.io.IoRecord
import dev.wasmo.brevity.io.IoResource
import dev.wasmo.brevity.io.IoService
import dev.wasmo.brevity.io.IoTopLevelUse
import dev.wasmo.brevity.io.IoToplevelWitPackage
import dev.wasmo.brevity.io.IoTypeAlias
import dev.wasmo.brevity.io.IoUse
import dev.wasmo.brevity.io.IoVariant
import dev.wasmo.brevity.io.IoWitPackage
import dev.wasmo.brevity.io.IoWorld

context(issueCollector: IssueCollector)
fun validateUniquePackageNames(
  toplevelPackages: List<IoToplevelWitPackage>,
): Map<IoPackageName, IoWitPackage>? {
  val witPackageMap = mutableMapOf<IoPackageName, MutableList<IoWitPackage>>()

  fun addPackage(packageName: IoPackageName, witPackage: IoWitPackage) {
    witPackageMap.getOrPut(packageName.normalized()) { mutableListOf() }.add(witPackage)
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
  val collisions = mutableMapOf<IoPackageName, List<IoWitPackage>>()
  val output = mutableMapOf<IoPackageName, IoWitPackage>()

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
  Map<IoServiceName, IoService>? {
  val services = mutableMapOf<IoServiceName, MutableList<IoService>>()

  fun addService(serviceName: IoServiceName, service: IoService) {
    validateUniqueInternalNames(service)
    when (service) {
      is IoInterface -> service.items
      is IoWorld -> service.items
    }.forEach { validateDeclaration(it) }

    services.getOrPut(serviceName.normalized()) { mutableListOf() }.add(service)
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
  val serviceNameCollisions = mutableListOf<Issue>()
  val output = mutableMapOf<IoServiceName, IoService>()

  for ((serviceName, serviceList) in services) {
    when (serviceList.size) {
      0 -> error("Invariant violated: service name exists without reference")
      1 -> output[serviceName] = serviceList.single()
      else -> {
        output[serviceName] = serviceList.first()
        val locations = serviceList.map { it.location }
        val issue = Issue("Duplicate definitions of $serviceName", locations)
        serviceNameCollisions += issue
        issueCollector.report(issue)
      }
    }
  }

  return if (serviceNameCollisions.isEmpty()) {
    output
  } else {
    null
  }
}

private fun processInlinePackage(
  pkg: IoInlinePackage,
  addService: (IoServiceName, IoService) -> Unit,
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

context(issueCollector: IssueCollector)
fun validateDeclaration(decl: IoDeclaration) {
  validateFlagCount(decl)

  validateUniqueInternalNames(decl)
}

context(issueCollector: IssueCollector)
private fun validateUniqueInternalNames(decl: IoDeclaration) {
  val namedDeclarations: List<IoNamedDeclaration> = when (decl) {
    is IoFlags -> decl.flags
    is IoFunction -> decl.parameters
    // This only handles collisions within an alias list. The following are not
    // handled and, since the inclusion target is not yet resolved, cannot be handled before
    // lowering to IR:
    //
    // * Collisions by an alias against an included name
    // * Collisions by any included name against names at the inclusion site
    is IoInclude -> decl.items
    is IoInterface -> decl.items.filterIsInstance<IoNamedDeclaration>()
    is IoRecord -> decl.fields
    is IoResource -> decl.functions
    is IoVariant -> decl.cases
    is IoUse -> decl.items
    is IoWorld -> decl.items.filterIsInstance<IoNamedDeclaration>()

    // Names defined in packages do need to be unique, but they are already handled by the service
    // name validator which also works across files
    is IoInlinePackage -> return

    // Declarations without need of internal duplicate checks
    is IoCase,
    is IoExternalApi,
    is IoField,
    is IoFlag,
    is IoInclude.Item,
    is IoEnum,
    is IoTypeAlias,
    is IoTopLevelUse,
    is IoUse.Item,
    is IoParameter,
      -> return
  }

  val collisions = namedDeclarations.groupBy { it.name.normalized() }
    .filterValues { it.size > 1 }

  for ((normalizedName, declarations) in collisions) {
    val firstName = declarations.first().name
    val displayName = if (declarations.all { it.name == firstName }) {
      firstName
    } else {
      normalizedName
    }

    val thingName = when (decl) {
      is IoFlags -> "flags"
      is IoFunction -> "parameters"
      is IoInclude -> "include aliases"
      is IoInterface -> "interface items"
      is IoRecord -> "fields"
      is IoResource -> "functions"
      is IoVariant -> "cases"
      is IoUse -> "use"
      is IoWorld -> "world items"

      is IoCase,
      is IoField,
      is IoFlag,
      is IoInclude.Item,
      is IoParameter,
      is IoEnum,
      is IoTypeAlias,
      is IoUse.Item,
      is IoExternalApi,
      is IoInlinePackage,
      is IoTopLevelUse,
        // None of these have subnames and so this should not happen.
        // If it does, though... hello, friend. You are now one step closer.
        -> "prisencolinensinainciusols"
    }

    issueCollector.report(
      Issue(
        "Duplicate $thingName named $displayName",
        declarations.map { it.location },
      ),
    )
  }
}

context(issueCollector: IssueCollector)
private fun validateFlagCount(decl: IoDeclaration) {
  if (decl !is IoFlags) return

  val flagCount = decl.flags.size
  if (flagCount > 32) {
    issueCollector.report(
      Issue(
        "Flags are limited to no more than 32 flags; $flagCount flags defined",
        listOf(decl.location),
      ),
    )
  }
}
