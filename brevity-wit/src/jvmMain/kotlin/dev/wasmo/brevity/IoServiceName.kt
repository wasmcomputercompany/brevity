package dev.wasmo.brevity

/**
 * The name of an interface or a world.
 */
data class ServiceName(
  override val packageName: PackageName,
  override val name: IoIdentifier,
): IoServiceName(packageName, name) {
  override fun constrain(): ServiceName = this
}

/**
 * The name of an interface or a world.
 *
 * An [IoServiceName] is as-read during initial parse, and may or may not contain malformed
 * identifiers.
 */
open class IoServiceName(
    open val packageName: IoPackageName,
    open val name: IoIdentifier,
) {
  fun normalized() = IoServiceName(packageName.normalized(), name.normalized())

  open fun constrain(): ServiceName =
    ServiceName(packageName.constrain(), name as Identifier)

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
