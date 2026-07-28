package dev.wasmo.brevity

/**
 * A high level issue with a wit file.
 */
class WitException(
  val issue: Issue,
) : IllegalStateException("${issue.description} at ${issue.location}") {
  constructor(
    description: String,
    location: Location,
  ) : this(
    Issue(
      description = description,
      location = location,
    ),
  )

  val location: Location
    get() = issue.location
}
