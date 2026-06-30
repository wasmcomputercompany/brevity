package dev.wasmo.brevity.kotlin.generator

import dev.wasmo.brevity.Identifier

/**
 * Converts `kabob-case` to `UpperCamelCase` or `lowerCamelCase`.
 */
internal fun Identifier.toCamelCase(upperCamel: Boolean): String {
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
