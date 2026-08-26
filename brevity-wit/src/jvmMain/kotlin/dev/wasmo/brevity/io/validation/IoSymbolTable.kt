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
class IoSymbolTable(
  private val packageNameMap: Map<PackageName, IoWitPackage>,
  private val serviceNameMap: Map<ServiceName, IoService>,
) {
  private val packageNameCaseInsensitiveMatchMap by lazy {
    packageNameMap.map { (packageName, _) -> packageName.normalized() to packageName }.toMap()
  }
  private val serviceNameCaseInsensitiveMatchMap by lazy {
    serviceNameMap.map { (serviceName, _) -> serviceName.normalized() to serviceName }.toMap()
  }

  operator fun get(packageName: PackageName?): IoWitPackage? = packageNameMap[packageName]

  operator fun get(serviceName: ServiceName?): IoService? = serviceNameMap[serviceName]

  fun getCaseInsensitiveMatch(packageName: PackageName?): PackageName?
    = packageNameCaseInsensitiveMatchMap[packageName?.normalized()]

  fun getCaseInsensitiveMatch(serviceName: ServiceName?): ServiceName?
    = serviceNameCaseInsensitiveMatchMap[serviceName?.normalized()]
}

context(issueCollector: IssueCollector)
fun List<IoToplevelWitPackage>.buildSymbolTable(): IoSymbolTable? {
  val packageNameMap = validateUniquePackageNames(this)
  val serviceNameMap = validateUniqueServiceNames(this)

  return if (packageNameMap != null && serviceNameMap != null) {
    IoSymbolTable(packageNameMap, serviceNameMap)
  } else {
    null
  }
}


