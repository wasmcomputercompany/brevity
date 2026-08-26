package dev.wasmo.brevity

sealed interface Identifier {
  val name: String

  fun normalized(): Identifier
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
): Identifier {
  override fun normalized(): Identifier = MalformedIdentifier(name.lowercase())
}

@JvmInline
value class WitIdentifier internal constructor(
  override val name: String,
): Identifier {
  override fun normalized(): Identifier = WitIdentifier(name.lowercase())

  override fun toString() = name
  /**
   * List of components that constitute this identifier.
   *
   * Each component is guaranteed non-empty.
   */
  fun components() = name.split("-")
}

private val identifierRegex = Regex("^%?([a-z][a-z0-9]*|[A-Z][A-Z0-9]*)(-[a-z0-9]+|-[A-Z0-9]+)*$")

/**
 * Parse [name] into an instance of [Identifier]. Valid WIT identifiers will be instances of
 * [WitIdentifier].
 */
fun Identifier(name: String): Identifier {
  return if (identifierRegex.matches(name)) {
    WitIdentifier(name)
  } else {
    MalformedIdentifier(name)
  }
}
