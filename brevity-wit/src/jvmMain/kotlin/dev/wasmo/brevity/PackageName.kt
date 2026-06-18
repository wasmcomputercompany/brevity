package dev.wasmo.brevity

data class PackageName(
  val namespaces: List<Identifier>,
  val names: List<Identifier>,
  val version: SemVer? = null,
) : Comparable<PackageName> {
  init {
    check(namespaces.isNotEmpty() && names.isNotEmpty())
  }

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

  val isLocal = namespaces == listOf(Identifier("local"))

  /**
   * Qualifies this package name using [packageName], a toplevel package name.
   *
   * If this package is already a toplevel package name, [qualify] noops.
   *
   * If [packageName] is itself a local package name, [qualify] throws.
   */
  fun qualify(packageName: PackageName): PackageName {
    return if (isLocal) {
      if (packageName.isLocal) {
        throw IllegalArgumentException("Cannot qualify using a local package name: $packageName")
      }
      PackageName(
        namespaces = packageName.namespaces,
        names = packageName.names + this.names,
      )
    } else {
      this
    }
  }
}
