package dev.wasmo.brevity

/**
 * A well-formed package name.
 */
data class PackageName(
  val namespaces: List<Identifier>,
  val names: List<Identifier>,
  val version: SemVer? = null,
) : Comparable<PackageName> {
  init {
    check(namespaces.isNotEmpty() && names.isNotEmpty())
  }

  fun normalized() = PackageName(
    namespaces = namespaces.map { it.normalized() },
    names = names.map { it.normalized() },
    version = version,
  )

  override fun compareTo(other: PackageName) = toString().compareTo(other.toString())

  override fun toString() = nameToString(this)
}
