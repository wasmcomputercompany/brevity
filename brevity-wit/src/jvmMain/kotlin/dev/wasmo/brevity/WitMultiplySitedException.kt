package dev.wasmo.brevity

/**
 * Like [WitException], but for issues that span multiple locations.
 */
class WitMultiplySitedException(
  val issue: String,
  val locations: List<Location>,
) : IllegalStateException(
  buildString {
    append(issue)
    for (location in locations) {
      appendLine()
      append("\tat $location")
    }
  },
)
