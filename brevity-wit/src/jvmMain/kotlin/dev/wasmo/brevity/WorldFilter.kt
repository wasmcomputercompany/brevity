package dev.wasmo.brevity

import dev.wasmo.brevity.ir.IrWitPackage
import dev.wasmo.brevity.ir.IrWorld
import java.util.TreeSet

/**
 * Returns a copy of [this] containing only the worlds named in [worldNames].
 *
 * @throws IllegalArgumentException if any name in [worldNames] doesn't identify a world.
 */
fun Collection<IrWitPackage>.filterNamedWorlds(
  worldNames: Collection<String>,
): List<IrWitPackage> {
  val leftoverWorldNames = worldNames.toMutableList()
  val acceptableWorldNames = TreeSet<String>()

  val result = map { irPackage ->
    irPackage.copy(
      services = irPackage.services.filter { item ->
        when (item) {
          is IrWorld -> {
            val itemAcceptableWorldNames = worldNames(item)
            acceptableWorldNames += itemAcceptableWorldNames

            // Returns true if this world name matches.
            leftoverWorldNames.removeAll(itemAcceptableWorldNames)
          }

          else -> true
        }
      },
    )
  }

  require(leftoverWorldNames.isEmpty()) {
    """
    |unexpected world name:
    |  ${leftoverWorldNames.joinToString(separator = "\n  ")}
    |not in acceptable set:
    |  ${acceptableWorldNames.joinToString(separator = "\n  ")}
    """.trimMargin()
  }

  return result
}

/** Returns all acceptable names for [irWorld]. */
fun worldNames(irWorld: IrWorld): Set<String> {
  return setOf(
    irWorld.serviceName.name.name,
    irWorld.serviceName.toString(),
    irWorld.serviceName.stripVersion().toString(),
  )
}

private fun ServiceName.stripVersion() = copy(
  packageName = packageName.copy(version = null),
)
