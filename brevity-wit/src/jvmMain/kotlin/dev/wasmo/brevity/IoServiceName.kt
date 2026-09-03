package dev.wasmo.brevity

sealed interface IoServiceName : Comparable<IoServiceName> {
  val packageName: IoPackageName
  val name: IoIdentifier

  /**
   * Method chainable shorthand for (it as [PackageName]) with a better error message
   */
  fun constrain(): ServiceName {
    return (this as? ServiceName) ?: error("Type narrowing to ServiceName failed")
  }

  fun normalized() = ServiceName(packageName.normalized(), name.normalized())

  override fun compareTo(other: IoServiceName) = toString().compareTo(other.toString())
}

fun ServiceName(
  packageName: PackageName,
  name: Identifier,
  ): ServiceName = WitServiceName(packageName, name)

fun ServiceName(
  packageName: IoPackageName,
  name: IoIdentifier,
) = if (packageName is PackageName && name is Identifier) {
  WitServiceName(packageName, name)
} else {
  MalformedServiceName(packageName, name)
}

fun createServiceName(
  packageName: IoPackageName,
  name: IoIdentifier,
) = if (packageName is PackageName && name is Identifier) {
  WitServiceName(packageName, name)
} else {
  MalformedServiceName(packageName, name)
}

sealed interface ServiceName : IoServiceName {
  override val packageName: PackageName
  override val name: Identifier
}

private data class WitServiceName(
  override val packageName: PackageName,
  override val name: Identifier,
): ServiceName {
  override fun toString() = renderString()

  override fun equals(other: Any?): Boolean {
    return toString() == other.toString()
  }

  override fun hashCode(): Int {
    return toString().hashCode()
  }
}

/**
 * The name of an interface or a world.
 *
 * An [IoServiceName] is as-read during initial parse, and may or may not contain malformed
 * identifiers.
 */
private data class MalformedServiceName(
    override val packageName: IoPackageName,
    override val name: IoIdentifier,
): IoServiceName {
  override fun equals(other: Any?): Boolean {
    return toString() == other.toString()
  }

  override fun hashCode(): Int {
    return toString().hashCode()
  }

  override fun toString() = renderString()
}

private fun IoServiceName.renderString(): String = buildString {
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
