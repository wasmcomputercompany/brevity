package dev.wasmo.brevity.kotlin.generator

import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.WitIdentifier

/**
 * Returns the `kabob-case` [Identifier.name] as `lower_snake_case`.
 */
val Identifier.lowerSnakeCase: String
  get() = toLowerSnakeCase()

private fun Identifier.toLowerSnakeCase(): String = if (this !is WitIdentifier) {
  error("Generating code for malformed identifier ${this.name}")
} else {
  return buildString {
    for (char in name) {
      when (char) {
        '-' -> {
          append('_')
          continue
        }

        in 'a'..'z' -> append(char)
        in 'A'..'Z' -> append(char - ('A' - 'a'))
        else -> append(char)
      }
    }
  }
}
