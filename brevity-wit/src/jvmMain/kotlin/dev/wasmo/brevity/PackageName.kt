package dev.wasmo.brevity

data class PackageName(
  val namespaces: List<IoIdentifier>,
  val names: List<IoIdentifier>,
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

  override fun toString() = buildString {
    for (name in namespaces) {
      append(name)
      append(':')
    }
    for ((index, name) in names.withIndex()) {
      if (index > 0) append('/')
      append(name)
    }
    if (version != null) {
      append("@")
      append(version.version)
    }
  }
}
