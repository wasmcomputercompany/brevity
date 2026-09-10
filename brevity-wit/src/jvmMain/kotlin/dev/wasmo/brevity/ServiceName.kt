package dev.wasmo.brevity

/**
 * The name of an interface or a world.
 *
 * An [ServiceName] is as-read during initial parse, and may or may not contain malformed
 * identifiers.
 */
data class ServiceName(
  val packageName: PackageName,
  val name: Identifier,
) : Comparable<ServiceName> {
  fun normalized() = ServiceName(
    packageName = packageName.normalized(),
    name = name.normalized(),
  )

  override fun compareTo(other: ServiceName) = toString().compareTo(other.toString())

  override fun toString() = nameToString(
    packageName = packageName,
    serviceName = name,
  )
}
