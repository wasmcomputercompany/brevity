package dev.wasmo.brevity

/**
 * A low level syntax parsing error.
 */
class WitSyntaxException(
  val description: String,
  val offset: Offset? = null,
) : IllegalStateException(buildString {
  append(description)
  if (offset != null) {
    append(":$offset")
  }
}
)
