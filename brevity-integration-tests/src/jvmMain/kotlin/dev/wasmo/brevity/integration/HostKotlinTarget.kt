package dev.wasmo.brevity.integration

import dev.wasmo.brevity.Orientation
import dev.wasmo.brevity.kotlin.generator.lowerCamelCase
import dev.wasmo.brevity.kotlin.generator.upperCamelCase
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.BufferedSink
import okio.FileSystem

class HostKotlinTarget(
  private val name: String,
  private val fileSystem: FileSystem,
  private val layout: ProjectLayout,
  private val testFunctions: List<TestFunction>,
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
      |import dev.wasmo.brevity.WasmInstance
      |import dev.wasmo.brevity.asyncWorld
      |import dev.wasmo.brevity.integration.isEqualTo
      |import dev.wasmo.brevity.wasi.p1.RealWasiP1Host
      |import dev.wasmo.brevity.wasi.p1.wasiP1World
      |import dev.wasmo.brevity.wasi.p2.RealWasiP2Host
      |import dev.wasmo.brevity.wasi.p2.wasiP2World
      |import dev.wasmo.brevity.wasi.p3.RealWasiP3Host
      |import dev.wasmo.brevity.wasi.p3.wasiP3FuturesStreamsWorld
      |import dev.wasmo.brevity.wasi.p3.wasiP3World
      |import kotlin.time.Duration
      |import kotlin.time.Duration.Companion.milliseconds
      |import kotlinx.coroutines.ExperimentalCoroutinesApi
      |import kotlinx.coroutines.delay
      |import kotlinx.coroutines.test.TestScope
      |import kotlinx.coroutines.test.currentTime
      |import kotlinx.coroutines.test.runTest
      |import okio.Path.Companion.toPath
      |import wit.brevity.testing.TestWorld
      |import wit.brevity.testing.World
      |
      |val world = TestWorld.World { TestWorldHostImplementation }
      |
      |
      """.trimMargin(),
    )

    val parentToTestFunctions = testFunctions.groupBy { it.functionParent }
    for ((parent, childFunctions) in parentToTestFunctions) {
      val functionsString = Buffer().run {
        for (testFunction in childFunctions.filter { it.orientation == Orientation.Import }) {
          with(testFunction) {
            kotlinDeclare()
          }
        }
        readUtf8().trim()
      }

      when (parent) {
        FunctionParent.WorldImport -> {
          writeUtf8(
            """
            |object TestWorldHostImplementation : TestWorld.Host {
            |
            """.trimMargin(),
          )
          for (memberService in parentToTestFunctions.keys) {
            when (memberService) {
              FunctionParent.InterfaceImport, FunctionParent.ResourceImport -> {
                writeUtf8(
                  """
                  |  override val ${memberService.serviceName.lowerCamelCase} = ${memberService.serviceName.upperCamelCase}HostImplementation
                  |
                  """.trimMargin(),
                )
              }

              else -> {
              }
            }
          }
          writeUtf8(
            """
            |  ${functionsString.replace("\n", "\n  ")}
            |}
            |
            |
            """.trimMargin(),
          )
        }

        FunctionParent.InterfaceImport -> {
          writeUtf8(
            """
            |object ${parent.serviceName.upperCamelCase}HostImplementation : wit.brevity.testing.${parent.serviceName.upperCamelCase} {
            |  ${functionsString.replace("\n", "\n  ")}
            |}
            |
            |
            """.trimMargin(),
          )
        }

        FunctionParent.ResourceImport -> {
          writeUtf8(
            """
            |object ${parent.serviceName.upperCamelCase}HostImplementation : wit.brevity.testing.${parent.serviceName.upperCamelCase} {
            |  override fun create() = ${parent.identifier.upperCamelCase}HostImplementation
            |}
            |
            |object ${parent.identifier.upperCamelCase}HostImplementation : wit.brevity.testing.${parent.serviceName.upperCamelCase}.${parent.identifier.upperCamelCase}() {
            |  ${functionsString.replace("\n", "\n  ")}
            |}
            |
            |
            """.trimMargin(),
          )
        }

        else -> continue
      }
    }

    writeUtf8(
      """
      |
      |fun main(vararg args: String) = runTest {
      |  val wasmInstance = WasmInstance(
      |    scope = this,
      |    path = args[0].toPath(),
      |    worlds = listOf(
      |      wasiP1World { RealWasiP1Host() },
      |      wasiP2World { RealWasiP2Host() },
      |      wasiP3FuturesStreamsWorld,
      |      wasiP3World { RealWasiP3Host() },
      |      asyncWorld,
      |      world,
      |    ),
      |  )
      |
      """.trimMargin(),
    )

    var testCount = 0
    for (testFunction in testFunctions) {
      for (testCase in testFunction.testCases) {
        val testIndex = testCount++
        if (testIndex > 0 && testIndex % 100 == 0) {
          writeUtf8(
            """
            |
            |  wasmInstance.gc()
            |
            |
            """.trimMargin(),
          )
        }

        with(testCase) {
          kotlinCallAndAssert()
        }
      }
    }

    writeUtf8(
      """
      |}
      |
      |fun assertk.Assert<BooleanArray>.isEqualTo(expected: BooleanArray) = transform { it.toList() }.isEqualTo(expected.toList())
      |
      |@OptIn(ExperimentalCoroutinesApi::class)
      |suspend fun TestScope.measureTestTime(block: suspend () -> Unit): Duration {
      |  val timeBefore = currentTime
      |  block()
      |  return (currentTime - timeBefore).milliseconds
      |}
      |
      """.trimMargin(),
    )
  }
}
