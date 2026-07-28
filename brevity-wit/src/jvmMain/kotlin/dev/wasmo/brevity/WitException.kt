package dev.wasmo.brevity

/**
 * A high level issue with a wit file.
 */
class WitException(
  val issue: Issue,
) : IllegalStateException(
  buildString {
    append(issue.description)

    when (issue.locations.size) {
      0 -> {}
      1 -> append(" at ${issue.locations.first()}")
      else -> for (location in issue.locations) {
        appendLine()
        append("\tat $location")
      }
    }
  },
) {
  constructor(
    description: String,
    location: Location,
  ) : this(
    Issue(
      description = description,
      location = location,
    ),
  )
}
