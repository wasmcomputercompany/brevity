package dev.wasmo.brevity.wasi.p2

import wit.wasi.cli.v0_2_0.Environment

class RealEnvironment : Environment {
  override fun getEnvironment(): List<Pair<String, String>> {
    return listOf()
  }

  override fun getArguments(): List<String> {
    return listOf()
  }

  override fun initialCwd(): String? {
    return null
  }
}
