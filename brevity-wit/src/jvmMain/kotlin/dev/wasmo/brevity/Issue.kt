package dev.wasmo.brevity

data class Issue(
  val description: String,
  val locations: List<Location>,
  val locationStack: List<Location> = emptyList(),
) {
  /**
   * @param description Description to display to user
   * @param location Location at which issue occurred
   * @param locationStack An optional sequence of enclosing locations for context, innermost first.
   */
  constructor(
    description: String,
    location: Location,
    vararg locationStack: Location,
  ) : this(description, listOf(location), locationStack.toList())
}
