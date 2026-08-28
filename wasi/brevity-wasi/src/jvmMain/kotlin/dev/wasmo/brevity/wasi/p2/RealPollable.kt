package dev.wasmo.brevity.wasi.p2

import wit.wasi.io.v0_2_0.Poll

class RealPollable : Poll.Pollable {
  override fun ready(): Boolean {
    return true
  }

  override fun block() {
  }
}
