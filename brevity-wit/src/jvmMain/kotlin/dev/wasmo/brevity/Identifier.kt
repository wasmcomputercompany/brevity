package dev.wasmo.brevity

/**
 * A well-formed WIT identifier like 'command' or 'get-insecure-random-u64'.
 */
@JvmInline
value class Identifier(
  val name: String,
) {
  init {
    check(identifierRegex.matches(name))
  }

  fun normalized() = Identifier(name.lowercase())

  override fun toString() = name

  /**
   * List of components that constitute this identifier.
   *
   * Each component is guaranteed non-empty.
   */
  fun components() = name.split("-")

  companion object {
    private val identifierRegex = Regex("^([a-z][a-z0-9]*|[A-Z][A-Z0-9]*)(-[a-z0-9]+|-[A-Z0-9]+)*$")

    /** Returns an identifier for this name, or null if it isn't a valid identifier. */
    fun String.toIdentifierOrNull(): Identifier? {
      val name = removePrefix("%")
      return try {
        Identifier(name)
      } catch (_: IllegalStateException) {
        null
      }
    }
  }
}
