package dev.wasmo.brevity.kotlin.generator

import dev.wasmo.brevity.IoIdentifier
import dev.wasmo.brevity.Identifier

/**
 * Returns the `kabob-case` [IoIdentifier.name] as `lower_snake_case`.
 */
val IoIdentifier.lowerSnakeCase: String
  get() = toLowerSnakeCase()

private fun IoIdentifier.toLowerSnakeCase(): String = if (this !is Identifier) {
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
