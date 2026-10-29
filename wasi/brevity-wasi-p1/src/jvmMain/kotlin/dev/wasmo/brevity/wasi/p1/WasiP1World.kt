package dev.wasmo.brevity.wasi.p1

import dev.wasmo.brevity.World
import wit.wasi.v0_1.BridgeWasi
import wit.wasi.v0_1.Wasi

fun wasiP1World(
  hostFactory: (guest: Wasi.Guest) -> Wasi.Host,
): World<Wasi.Host, Wasi.Guest> {
  val guest = BridgeWasi.BridgeGuest()
  val host = hostFactory(guest)
  return BridgeWasi(guest, host)
}
