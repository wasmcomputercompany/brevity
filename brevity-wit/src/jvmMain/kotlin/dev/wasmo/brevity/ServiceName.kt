package dev.wasmo.brevity

/**
 * The name of an interface or a world.
 */
data class ServiceName(
  val packageName: PackageName,
  val name: Identifier,
) {
  fun normalized() = ServiceName(packageName.normalized(), name.normalized())

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
