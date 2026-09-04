package dev.wasmo.brevity

/**
 * An [IoPackageName] is as-read during initial parse, and may or may not contain malformed
 * identifiers.
 */
sealed interface IoPackageName : Comparable<IoPackageName> {
  val namespaces: List<IoIdentifier>
  val names: List<IoIdentifier>
  val version: SemVer?

  /**
   * Method chainable shorthand for (it as [PackageName]) with a better error message
   */
  fun constrain(): PackageName {
    return (this as? PackageName) ?: error("Type narrowing to PackageName failed")
  }

  override fun compareTo(other: IoPackageName) = toString().compareTo(other.toString())

  fun normalized() = PackageName(
    namespaces = namespaces.map { it.normalized() },
    names = names.map { it.normalized() },
    version = version,
  )
}

/**
 * Represents a well-formed package name.
 */
sealed interface PackageName : IoPackageName {
  override val namespaces: List<Identifier>
  override val names: List<Identifier>
}

fun PackageName(
  namespaces: List<Identifier>,
  names: List<Identifier>,
  version: SemVer? = null,
): PackageName {
  check(namespaces.isNotEmpty() && names.isNotEmpty())

  return WitPackageName(namespaces, names, version)
}

fun PackageName(
  namespaces: List<IoIdentifier>,
  names: List<IoIdentifier>,
  version: SemVer? = null,
): IoPackageName {
  check(namespaces.isNotEmpty() && names.isNotEmpty())

  val wellFormedNamespaces = namespaces.filterIsInstance<Identifier>()
  val wellFormedNames = names.filterIsInstance<Identifier>()
  return if (wellFormedNames.size == names.size && wellFormedNamespaces.size == namespaces.size) {
    WitPackageName(wellFormedNamespaces, wellFormedNames, version)
  } else {
    MalformedPackageName(namespaces, names, version)
  }
}

private data class WitPackageName(
  override val namespaces: List<Identifier>,
  override val names: List<Identifier>,
  override val version: SemVer? = null,
): PackageName {
  override fun toString() = toStringImpl()

  override fun equals(other: Any?): Boolean = equalsImpl(other)

  override fun hashCode(): Int = hashCodeImpl()
}


private data class MalformedPackageName(
  override val namespaces: List<IoIdentifier>,
  override val names: List<IoIdentifier>,
  override val version: SemVer? = null,
) : IoPackageName {
  override fun toString() = toStringImpl()

  override fun equals(other: Any?): Boolean = equalsImpl(other)

  override fun hashCode(): Int = hashCodeImpl()
}

private fun IoPackageName.hashCodeImpl(): Int = toString().hashCode()

private fun IoPackageName.equalsImpl(other: Any?): Boolean = other is IoPackageName &&
  namespaces == other.namespaces &&
  names == other.names &&
  version == other.version

private fun IoPackageName.toStringImpl(): String = buildString {
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
