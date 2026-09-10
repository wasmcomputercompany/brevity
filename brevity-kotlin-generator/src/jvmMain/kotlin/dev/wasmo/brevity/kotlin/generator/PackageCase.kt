package dev.wasmo.brevity.kotlin.generator

import dev.wasmo.brevity.Identifier

/**
 * Returns the `packagecase` [Identifier.name].
 */
val Identifier.packageCase: String
  get() = toPackageCase()

private fun Identifier.toPackageCase() = buildString {
  for (char in name) {
    when (char) {
      '-' -> continue
      in 'a'..'z' -> append(char)
      in 'A'..'Z' -> append(char - ('A' - 'a'))
      else -> append(char)
    }
  }
}
