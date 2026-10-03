package dev.wasmo.brevity.integration

sealed interface CallingMechanism {
  data object Sync : CallingMechanism
  data class Async(val sleep: Boolean = false) : CallingMechanism {
    override val upperCamelSuffix: String
      get() = "Async"
  }

  val upperCamelSuffix: String
    get() = ""
}
