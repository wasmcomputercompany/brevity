package dev.wasmo.brevity

import dev.wasmo.brevity.io.IoDeclaration
import dev.wasmo.brevity.io.UsePath

/**
 * The name of an interface or a world.
 */
data class ServiceName(
  val packageName: PackageName,
  val name: Identifier,
) {
  val usePath: UsePath
    get() = UsePath(packageName, name)

  override fun toString() = buildString {
    for (namespace in packageName.namespaces) {
      append(namespace)
      append(':')
    }
    for (packageName in packageName.names) {
      append(packageName)
      append('/')
    }
    append(name)
    if (packageName.version != null) {
      append('@')
      append(packageName.version)
    }
  }
}
