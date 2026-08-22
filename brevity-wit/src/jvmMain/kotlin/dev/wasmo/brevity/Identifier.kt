package dev.wasmo.brevity

import dev.wasmo.brevity.io.isDigit
import dev.wasmo.brevity.io.isLowerCase
import dev.wasmo.brevity.io.isUpperCase

sealed interface Identifier {
  val name: String
}

@JvmInline
value class MalformedIdentifier internal constructor(
  override val name: String,
): Identifier

@JvmInline
value class WitIdentifier internal constructor(
  override val name: String,
): Identifier {
  override fun toString() = name
  /**
   * List of components that constitute this identifier.
   *
   * Each component is guaranteed non-empty.
   */
  fun components() = name.split("-")
}

private val identifierRegex = Regex("^%?([a-z][a-z0-9]*|[A-Z][A-Z0-9]*)(-[a-z0-9]+|-[A-Z0-9]+)*$")

fun Identifier(name: String): Identifier {
  return if (identifierRegex.matches(name)) {
    WitIdentifier(name)
  } else {
    MalformedIdentifier(name)
  }
}
