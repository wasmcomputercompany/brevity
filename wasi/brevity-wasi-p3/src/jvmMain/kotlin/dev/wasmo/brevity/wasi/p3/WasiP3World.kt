package dev.wasmo.brevity.wasi.p3

import dev.wasmo.brevity.World
import wit.wasi.cli.v0_3_1.Imports
import wit.wasi.cli.v0_3_1.World

fun wasiP3World(
  hostFactory: (guest: Imports.Guest) -> Imports.Host,
): World<Imports.Host, Imports.Guest> = Imports.World(hostFactory)
