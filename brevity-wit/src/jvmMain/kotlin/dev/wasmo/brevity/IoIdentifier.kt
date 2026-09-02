package dev.wasmo.brevity

sealed interface IoIdentifier {
  val name: String

  fun normalized(): IoIdentifier
}

/**
 * Private implementation used exclusively to represent malformed identifiers.
 *
 * Would prefer to do something like this:
 * value class WitIdentifier : IoIdentifier
 * value class IoIdentifier
 *
 * ...but value classes are required to be final.
 *
 * Value classes are pretty restrictive, as it turns out!
 */
@JvmInline
private value class MalformedIdentifier(
  override val name: String,
): IoIdentifier {
  override fun normalized(): IoIdentifier = MalformedIdentifier(name.lowercase())
}

@JvmInline
value class Identifier private constructor(
  override val name: String,
): IoIdentifier {
  override fun normalized(): IoIdentifier = Identifier(name.lowercase())

  override fun toString() = name
  /**
   * List of components that constitute this identifier.
   *
   * Each component is guaranteed non-empty.
   */
  fun components() = name.split("-")

  companion object {
    /**
     * Parse [name] into an instance of [Identifier]. Valid WIT identifiers will be instances of
     * [Identifier].
     */
    fun Identifier(name: String): IoIdentifier {
      return if (identifierRegex.matches(name)) {
        dev.wasmo.brevity.Identifier(name.removePrefix("%"))
      } else {
        MalformedIdentifier(name.removePrefix("%"))
      }
    }
  }
}

private val identifierRegex = Regex("^%?([a-z][a-z0-9]*|[A-Z][A-Z0-9]*)(-[a-z0-9]+|-[A-Z0-9]+)*$")

