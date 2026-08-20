package dev.wasmo.brevity.kotlin.generator

import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.MalformedIdentifier
import dev.wasmo.brevity.WitIdentifier

/**
 * Returns the `kabob-case` [Identifier.name] as `lowerCamelCase`.
 */
val Identifier.lowerCamelCase: String
  get() = toCamelCase(false)

/**
 * Returns the `kabob-case` [Identifier.name] as `UpperCamelCase`.
 */
val Identifier.upperCamelCase: String
  get() = toCamelCase(true)

private fun Identifier.toCamelCase(upperCamel: Boolean): String = when (this) {
  is MalformedIdentifier -> error("Generating code for malformed identifier ${this.name}")
  is WitIdentifier ->
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
