package dev.wasmo.brevity.kotlin.generator

import dev.wasmo.brevity.IoIdentifier
import dev.wasmo.brevity.Identifier

/**
 * Returns the `kabob-case` [IoIdentifier.name] as `lowerCamelCase`.
 */
val IoIdentifier.lowerCamelCase: String
  get() = toCamelCase(false)

/**
 * Returns the `kabob-case` [IoIdentifier.name] as `UpperCamelCase`.
 */
val IoIdentifier.upperCamelCase: String
  get() = toCamelCase(true)

private fun IoIdentifier.toCamelCase(upperCamel: Boolean): String = if (this !is Identifier) {
  error("Generating code for malformed identifier ${this.name}")
} else {
  return buildString {
    var uppercase = upperCamel
    for (char in name) {
      when (char) {
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
