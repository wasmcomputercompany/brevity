package dev.wasmo.brevity.integration

enum class CallingMechanism {
  Sync {
    override val upperCamelSuffix: String
      get() = ""
  },

  Async {
    override val upperCamelSuffix: String
      get() = "Async"
  };

  abstract val upperCamelSuffix: String
}
