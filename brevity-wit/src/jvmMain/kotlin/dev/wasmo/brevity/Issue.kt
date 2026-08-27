package dev.wasmo.brevity

data class Issue(
  val description: String,
  val locations: List<Location>,
  val locationStack: List<Location> = emptyList(),
) {
  constructor(
    description: String,
    location: Location,
  ) : this(description, listOf(location), emptyList())
  constructor(
    description: String,
    location: Location,
    locationStack: Location,
  ) : this(description, listOf(location), listOf(locationStack))

}
