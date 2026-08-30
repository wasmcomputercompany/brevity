package dev.wasmo.brevity.kotlin.generator

import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.WitIdentifier

/**
 * Returns the `packagecase` [Identifier.name].
 */
val Identifier.packageCase: String
  get() = toPackageCase()

private fun Identifier.toPackageCase(): String = if (this !is WitIdentifier) {
  error("Generating code for malformed identifier ${this.name}")
} else {
  return buildString {
    for (char in name) {
      when (char) {
        '-' -> continue
        in 'a'..'z' -> append(char)
        in 'A'..'Z' -> append(char - ('A' - 'a'))
        else -> append(char)
      }
    }
  }
}
