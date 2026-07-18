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
      1 -> appendLocation(" ", issue.locations.first())
      else -> for (location in issue.locations) {
        appendLine()
        appendLocation("\t", location)
      }
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
}

private fun StringBuilder.appendLocation(spacing: String, location: Location) {
  val (path, offset) = location
  append("${spacing}at $path")
  if (offset != null) {
    append(":$offset")
  }
}
