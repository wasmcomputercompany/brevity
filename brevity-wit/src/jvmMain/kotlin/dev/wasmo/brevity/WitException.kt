package dev.wasmo.brevity

/**
 * A high level issue with a wit file.
 */
class WitException(
  val issue: Issue,
) : IllegalStateException(
  buildString {
    append(issue.description)
    val (path, offset) = issue.location
    append(" at $path")
    if (offset != null) {
      append(":$offset")
    }
  },
) {
  constructor(
    description: String,
    path: String,
    offset: Offset? = null,
  ) : this(
    Issue(
      description = description,
      location = Location(path, offset)
    )
  )

  val offset: Offset? = issue.location.offset
}
