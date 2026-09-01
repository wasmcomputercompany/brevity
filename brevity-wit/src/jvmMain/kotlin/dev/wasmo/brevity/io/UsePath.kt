package dev.wasmo.brevity.io

import dev.wasmo.brevity.IoIdentifier
import dev.wasmo.brevity.PackageName
import dev.wasmo.brevity.SemVer
import dev.wasmo.brevity.ServiceName

/**
 * This is a package name plus an interface name, or just an interface name. The encoded form always
 * puts the version at the end of the entire string.
 */
data class UsePath(
    val packageName: PackageName?,
    val name: IoIdentifier,
) {
  companion object {
    operator fun invoke(
        namespaces: List<IoIdentifier> = listOf(),
        packageNames: List<IoIdentifier> = listOf(),
        name: IoIdentifier,
        version: SemVer? = null,
    ) = UsePath(
      PackageName(
        namespaces = namespaces,
        names = packageNames,
        version = version,
      ),
      name,
    )

    operator fun invoke(name: IoIdentifier) = UsePath(null, name)
  }

  override fun toString(): String {
    return when {
      packageName != null -> ServiceName(packageName, name).toString()
      else -> name.name
    }
  }
}
