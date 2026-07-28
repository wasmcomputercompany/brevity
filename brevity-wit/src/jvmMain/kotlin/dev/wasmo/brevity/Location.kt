package dev.wasmo.brevity

import okio.Path
import okio.Path.Companion.toPath

data class Location(
  val path: Path,
  val line: Int? = null,
  val column: Int? = null,
) {
  init {
    require((line == null || line > 0) && (column == null || column > 0))
  }

  constructor(
    path: String,
    line: Int? = null,
    column: Int? = null,
  ) : this(path.toPath(), line, column)

  fun at(line: Int?, column: Int?) = Location(path, line, column)

  override fun toString() = when {
    line != null && column != null -> "$path:$line:$column"
    else -> path.toString()
  }
}

fun Path.location(line: Int? = null, column: Int? = null) = Location(
  path = this,
  line = line,
  column = column,
)
