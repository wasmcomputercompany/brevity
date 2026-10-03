package dev.wasmo.brevity.integration

enum class Orientation {
  /** Host calls guest. */
  Export {
    override val keyword: String
      get() = "export"
  },

  /** Guest calls host. We also generate a 'trampoline' export function to get to the guest. */
  Import {
    override val keyword: String
      get() = "import"
  };

  abstract val keyword: String
}
