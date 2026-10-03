package dev.wasmo.brevity.integration

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.BufferedSink
import okio.FileSystem

class HostKotlinTarget(
  private val name: String,
  private val fileSystem: FileSystem,
  private val layout: ProjectLayout,
  private val types: List<SampleType>,
) {
  suspend fun generate() {
    withContext(Dispatchers.IO + CoroutineName("HostKotlinTarget")) {
      val packagePath = layout.hostSrc / "dev/wasmo/brevity/integration"
      val mainPath = packagePath / "Host${name.replaceFirstChar { it.uppercase() }}Main.kt"
      fileSystem.createDirectories(packagePath)
      fileSystem.write(mainPath) { writeKotlin() }
    }
  }

  private fun BufferedSink.writeKotlin() {
    writeUtf8(
      """
      |@file:OptIn(dev.wasmo.brevity.BrevityInternalApi::class)
      |
      |package dev.wasmo.brevity.integration
      |
      |import assertk.assertThat
      |import assertk.assertions.isEqualTo
      |import assertk.assertions.isGreaterThanOrEqualTo
      |import dev.wasmo.brevity.Async
      |import dev.wasmo.brevity.RealAsyncHost
      |import dev.wasmo.brevity.WasmInstance
      |import dev.wasmo.brevity.World
      |import dev.wasmo.brevity.integration.isEqualTo
      |import dev.wasmo.brevity.wasi.p1.RealWasiP1Host
      |import dev.wasmo.brevity.wasi.p2.RealWasiP2Host
      |import kotlin.time.Duration.Companion.milliseconds
      |import kotlin.time.measureTime
      |import kotlinx.coroutines.test.runTest
      |import okio.Path.Companion.toPath
      |import wit.brevity.testing.BrevityTest
      |import wit.brevity.testing.World
      |import wit.wasi.cli.v0_2_0.World
      |import wit.wasi.v0_1.World
      |
      |object RealHost : BrevityTest.Host {
      |
      """.trimMargin(),
    )
    for (testFunction in types.testFunctions) {
      with(testFunction) {
        kotlinDeclareHostFunctions()
      }
    }
    writeUtf8(
      """
      |}
      |
      |val world = BrevityTest.World { RealHost }
      |
      |fun main(vararg args: String) = runTest {
      |  WasmInstance(
      |    path = args[0].toPath(),
      |    worlds = listOf(
      |      wit.wasi.v0_1.Wasi.World({ RealWasiP1Host() }),
      |      wit.wasi.cli.v0_2_0.Imports.World({ RealWasiP2Host() }),
      |      Async.World { RealAsyncHost() },
      |      world,
      |    ),
      |  )
      |
      |
      """.trimMargin(),
    )

    for (testFunction in types.testFunctions) {
      for (testCase in testFunction.testCases) {
        with(testCase) {
          kotlinCallAndAssert("world.guest")
        }
      }
    }

    writeUtf8(
      """
      |}
      |
      |fun assertk.Assert<BooleanArray>.isEqualTo(expected: BooleanArray) = transform { it.toList() }.isEqualTo(expected.toList())
      |
      """.trimMargin(),
    )
  }
}
