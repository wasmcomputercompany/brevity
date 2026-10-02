package dev.wasmo.brevity.integration

sealed interface PassingMechanism {
  data class PassAsParameter(
    /** Extra parameters to force store instead of lower-flat. */
    val padding: Int = 0,
  ) : PassingMechanism

  data object PassAsReturnValue : PassingMechanism
}
