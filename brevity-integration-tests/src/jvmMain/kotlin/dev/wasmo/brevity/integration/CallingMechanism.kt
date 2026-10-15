package dev.wasmo.brevity.integration

enum class CallingMechanism {
  Sync {
    override val upperCamelSuffix: String
      get() = ""
    override val passingMechanisms: List<PassingMechanism>
      get() = listOf(
        PassingMechanism.Parameter(),
        PassingMechanism.Parameter(16),
        PassingMechanism.Return,
      )
  },

  Async {
    override val upperCamelSuffix: String
      get() = "Async"
    override val passingMechanisms: List<PassingMechanism>
      get() = listOf(
        PassingMechanism.Parameter(),
        PassingMechanism.Parameter(4),
        PassingMechanism.Return,
      )
  };

  abstract val upperCamelSuffix: String

  abstract val passingMechanisms: List<PassingMechanism>
}
