package dev.wasmo.brevity.integration

import dev.wasmo.brevity.WasmInstance
import dev.wasmo.brevity.wasi.p1.wasiP1World
import dev.wasmo.brevity.wasi.p2.RealWasiP2Host
import dev.wasmo.brevity.wasi.p2.wasiP2World
import dev.wasmo.brevity.wasi.p3.RealWasiP3Host
import dev.wasmo.brevity.wasi.p3.wasiP3World
import okio.Path.Companion.toPath
import wit.wasmo.testing.WasmoTesting
import wit.wasmo.testing.World

class BrevityIntegrationTester {
  private val kotlinWasmPath =
    "build/compileSync/wasmWasi/main/developmentExecutable/kotlin/brevity-root-brevity-integration-tests.wasm".toPath()

  private val host = object : WasmoTesting.Host {
  }

  val world = WasmoTesting.World { host }
  val wasiP1 = FakeWasi()

  val wasmInstance = WasmInstance(
    path = kotlinWasmPath,
    worlds = listOf(
      wasiP1World { wasiP1 },
      wasiP2World { RealWasiP2Host() },
      wasiP3World { RealWasiP3Host() },
      world,
    ),
  ).apply {
    // GC after setting everything up, until our GC correctness issue is fixed.
    gc()
  }
}
