package dev.wasmo.brevity.wasi.p3

import wit.wasi.cli.v0_3_0.Environment

class RealEnvironment : Environment {
  override fun getEnvironment(): List<Pair<String, String>> {
    return listOf()
  }

  override fun getArguments(): List<String> {
    return listOf()
  }

  override fun getInitialCwd(): String? {
    return null
  }
}
