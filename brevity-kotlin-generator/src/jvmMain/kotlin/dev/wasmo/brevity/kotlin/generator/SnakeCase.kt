package dev.wasmo.brevity.kotlin.generator

import dev.wasmo.brevity.Identifier

/**
 * Returns the `kabob-case` [Identifier.name] as `lower_snake_case`.
 */
val Identifier.lowerSnakeCase: String
  get() = toLowerSnakeCase()

private fun Identifier.toLowerSnakeCase() = buildString {
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
