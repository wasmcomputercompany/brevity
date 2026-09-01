package dev.wasmo.brevity.kotlin.generator

import dev.wasmo.brevity.IoIdentifier
import dev.wasmo.brevity.Identifier

/**
 * Returns the `packagecase` [IoIdentifier.name].
 */
val IoIdentifier.packageCase: String
  get() = toPackageCase()

private fun IoIdentifier.toPackageCase(): String = if (this !is Identifier) {
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
