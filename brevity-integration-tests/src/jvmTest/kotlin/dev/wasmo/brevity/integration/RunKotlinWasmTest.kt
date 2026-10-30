package dev.wasmo.brevity.integration

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.wasmo.brevity.WasmInstance
import dev.wasmo.brevity.asyncWorld
import dev.wasmo.brevity.wasi.p1.wasiP1World
import dev.wasmo.brevity.wasi.p2.RealWasiP2Host
import dev.wasmo.brevity.wasi.p2.wasiP2World
import dev.wasmo.brevity.wasi.p3.RealWasiP3Host
import dev.wasmo.brevity.wasi.p3.wasiP3World
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import wit.wasmo.testing.StringArgument
import wit.wasmo.testing.StringResult
import wit.wasmo.testing.Types
import wit.wasmo.testing.WasmoTesting
import wit.wasmo.testing.World

class RunKotlinWasmTest {
  private val kotlinWasmPath = "build/compileSync/wasmWasi/main/developmentExecutable/kotlin/brevity-root-brevity-integration-tests.wasm".toPath()
  private val host = object : WasmoTesting.Host {
    override val testingTypes: Types
      get() = object : Types {}
  }

  @Test
  fun `call function declared on world`() = runTest {
    val world = WasmoTesting.World { host }
    val wasiP1 = FakeWasi()
    WasmInstance(
      scope = this,
      path = kotlinWasmPath,
      worlds = listOf(
        wasiP1World { wasiP1 },
        wasiP2World { RealWasiP2Host() },
        wasiP3World { RealWasiP3Host() },
        asyncWorld,
        world,
      ),
    )
    val result = world.guest.sum(5L, 10L)
    assertThat(result).isEqualTo(15L)
  }

  @Test
  fun `call function declared on interface`() = runTest {
    val world = WasmoTesting.World { host }
    val wasiP1 = FakeWasi()
    WasmInstance(
      scope = this,
      path = kotlinWasmPath,
      worlds = listOf(
        wasiP1World { wasiP1 },
        wasiP2World { RealWasiP2Host() },
        wasiP3World { RealWasiP3Host() },
        asyncWorld,
        world,
      ),
    )
    val result = world.guest.calculator.multiply(5L, 10L)
    assertThat(result).isEqualTo(50L)
  }

  @Test
  fun `call concatenate`() = runTest {
    val world = WasmoTesting.World { host }
    val wasiP1 = FakeWasi()
    WasmInstance(
      scope = this,
      path = kotlinWasmPath,
      worlds = listOf(
        wasiP1World { wasiP1 },
        wasiP2World { RealWasiP2Host() },
        wasiP3World { RealWasiP3Host() },
        asyncWorld,
        world,
      ),
    )
    val a = "Hello, ".asStringArgument()
    val b = "World!".asStringArgument()

    var result: String? = null
    val callback = object : StringResult() {
      override fun put(value_: String) {
        result = value_
      }
    }

    world.guest.concat(a, b, callback)
    assertThat(result).isEqualTo("Hello, World!")
  }

  @Test
  fun `call inline concatenate`() = runTest {
    val world = WasmoTesting.World { host }
    val wasiP1 = FakeWasi()
    WasmInstance(
      scope = this,
      path = kotlinWasmPath,
      worlds = listOf(
        wasiP1World { wasiP1 },
        wasiP2World { RealWasiP2Host() },
        wasiP3World { RealWasiP3Host() },
        asyncWorld,
        world,
      ),
    )
    assertThat(world.guest.inlineConcat("Hello, ", "World!"))
      .isEqualTo("Hello, World!")
  }

  private fun String.asStringArgument() = object : StringArgument() {
    override fun get() = this@asStringArgument
  }

  @Test
  fun `call printGreeting`() = runTest {
    val world = WasmoTesting.World { host }
    val wasiP1 = FakeWasi()
    WasmInstance(
      scope = this,
      path = kotlinWasmPath,
      worlds = listOf(
        wasiP1World { wasiP1 },
        wasiP2World { RealWasiP2Host() },
        wasiP3World { RealWasiP3Host() },
        asyncWorld,
        world,
      ),
    )
    val name = "Jesse".asStringArgument()
    world.guest.streams.printGreeting(name)

    assertThat(wasiP1.stdout.readUtf8()).isEqualTo("Hello, Jesse\n")
  }

  @Test
  fun `call printError`() = runTest {
    val world = WasmoTesting.World { host }
    val wasiP1 = FakeWasi()
    WasmInstance(
      scope = this,
      path = kotlinWasmPath,
      worlds = listOf(
        wasiP1World { wasiP1 },
        wasiP2World { RealWasiP2Host() },
        wasiP3World { RealWasiP3Host() },
        asyncWorld,
        world,
      ),
    )
    val name = "Jesse".asStringArgument()
    world.guest.streams.printError(name)

    assertThat(wasiP1.stderr.readUtf8()).isEqualTo("Exception: boom, Jesse!\n\n")
  }

  @Test
  fun `parallel sleep`() = runTest {
    val world = WasmoTesting.World { host }
    val wasiP1 = FakeWasi()
    WasmInstance(
      scope = this,
      path = kotlinWasmPath,
      worlds = listOf(
        wasiP1World { wasiP1 },
        wasiP2World { RealWasiP2Host() },
        wasiP3World { RealWasiP3Host() },
        asyncWorld,
        world,
      ),
    )

    val elapsed = measureTestTime {
      world.guest.parallelSleep(30U, 100UL)
    }
    assertThat(elapsed).isEqualTo(100.milliseconds)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  suspend fun TestScope.measureTestTime(block: suspend () -> Unit): Duration {
    val timeBefore = currentTime
    block()
    return (currentTime - timeBefore).milliseconds
  }
}
