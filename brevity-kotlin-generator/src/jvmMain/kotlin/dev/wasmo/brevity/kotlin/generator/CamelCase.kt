package dev.wasmo.brevity.kotlin.generator

import dev.wasmo.brevity.Identifier

/**
 * Returns the `kabob-case` [Identifier.name] as `lowerCamelCase`.
 */
internal val Identifier.lowerCamelCase: String
  get() = toCamelCase(false)

/**
 * Returns the `kabob-case` [Identifier.name] as `UpperCamelCase`.
 */
internal val Identifier.upperCamelCase: String
  get() = toCamelCase(true)

private fun Identifier.toCamelCase(upperCamel: Boolean): String {
  return buildString {
    var uppercase = upperCamel
    for (char in name) {
      when (char) {
        '%' -> continue
        '-' -> {
          uppercase = true
          continue
        }

        in 'a'..'z' if uppercase -> append(char - ('a' - 'A'))
        in 'A'..'Z' if !uppercase -> append(char - ('A' - 'a'))
        else -> append(char)
      }
      uppercase = false
    }
  }
}
