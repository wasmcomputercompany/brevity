package dev.wasmo.brevity

import okio.Path

data class Location(
  val path: String,
  val offset: Offset?,
)

fun Path.location(offset: Offset? = null) = Location(this.toString(), offset)
