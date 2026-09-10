package dev.wasmo.brevity

/**
 * Convert a symbol name to a string.
 *
 * This can be used for packages, services, and types. All of these share common syntax.
 */
internal fun nameToString(
  packageName: PackageName,
  serviceName: Identifier? = null,
  typeName: Identifier? = null,
): String {
  require(typeName == null || serviceName != null) {
    "cannot have a type name without a service name"
  }

  return buildString {
    for (namespace in packageName.namespaces) {
      append(namespace)
      append(':')
    }
    for ((i, name) in packageName.names.withIndex()) {
      if (i > 0) append('/')
      append(name)
    }
    if (serviceName != null) {
      append('/')
      append(serviceName)
      if (typeName != null) {
        append('.')
        append(typeName)
      }
    }
    if (packageName.version != null) {
      append('@')
      append(packageName.version)
    }
  }
}
