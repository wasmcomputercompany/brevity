package dev.wasmo.brevity.ir

import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.PackageName
import dev.wasmo.brevity.io.UsePath

/**
 * The name of an interface or a world.
 */
data class IrServiceName(
  val packageName: PackageName,
  val name: Identifier,
) {
  val usePath: UsePath
    get() = UsePath(packageName, name)

  override fun toString() = usePath.toString()
}
