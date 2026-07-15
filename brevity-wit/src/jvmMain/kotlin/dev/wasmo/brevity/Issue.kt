package dev.wasmo.brevity

data class Issue(
  val description: String,
  val locations: List<Location>,
) {
  constructor(
    description: String,
    location: Location,
  ) : this(description, listOf(location))
}
