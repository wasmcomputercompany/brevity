package dev.wasmo.brevity.io.validation

import dev.wasmo.brevity.IssueCollector
import dev.wasmo.brevity.PackageName
import dev.wasmo.brevity.ServiceName
import dev.wasmo.brevity.io.IoService
import dev.wasmo.brevity.io.IoToplevelWitPackage
import dev.wasmo.brevity.io.IoWitPackage

/**
 * A set of toplevel wit packages, validated and with validation prerequisite lookup information
 */
class ValidatedIoWitPackages(
  val toplevelPackages: List<IoToplevelWitPackage>,
  val packageNameMap: Map<PackageName, IoWitPackage>,
  val serviceNameMap: Map<ServiceName, IoService>,
)

context(issueCollector: IssueCollector)
fun List<IoToplevelWitPackage>.validate(): ValidatedIoWitPackages? {
  val packageNameMap = validateUniquePackageNames(this)
  val serviceNameMap = validateUniqueServiceNames(this)

  return if (packageNameMap != null && serviceNameMap != null) {
    ValidatedIoWitPackages(this, packageNameMap, serviceNameMap)
  } else {
    null
  }
}


