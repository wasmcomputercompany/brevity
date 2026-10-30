package dev.wasmo.brevity.wasi.p2

import dev.wasmo.brevity.World
import wit.wasi.cli.v0_2_0.Imports
import wit.wasi.cli.v0_2_0.World

fun wasiP2World(
  hostFactory: (guest: Imports.Guest) -> Imports.Host,
): World<Imports.Host, Imports.Guest> = Imports.World(hostFactory)
