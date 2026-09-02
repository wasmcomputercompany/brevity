package dev.wasmo.brevity

/**
 * The name of a package
 */
data class PackageName(
  override val namespaces: List<Identifier>,
  override val names: List<Identifier>,
  override val version: SemVer? = null,
): IoPackageName(namespaces, names, version) {
  override fun constrain(): PackageName = this
}

/**
 * The name of a package
 *
 * An [IoPackageName] is as-read during initial parse, and may or may not contain malformed
 * identifiers.
 */
open class IoPackageName(
  open val namespaces: List<IoIdentifier>,
  open val names: List<IoIdentifier>,
  open val version: SemVer? = null,
) : Comparable<IoPackageName> {
  init {
    check(namespaces.isNotEmpty() && names.isNotEmpty())
  }

  fun normalized() = IoPackageName(
    namespaces = namespaces.map { it.normalized() },
    names = names.map { it.normalized() },
    version = version,
  )

  override fun compareTo(other: IoPackageName) = toString().compareTo(other.toString())

  override fun toString() = buildString {
    for (name in namespaces) {
      append(name)
      append(':')
    }
    for ((index, name) in names.withIndex()) {
      if (index > 0) append('/')
      append(name)
    }
    version?.let {
      append("@")
      append(it.version)
    }
  }

  open fun constrain(): PackageName = PackageName(
    namespaces = namespaces.map { it as Identifier },
    names = names.map { it as Identifier },
    version = version,
  )
}
