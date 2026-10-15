package dev.wasmo.brevity.integration

import dev.wasmo.brevity.kotlin.generator.lowerCamelCase
import dev.wasmo.brevity.kotlin.generator.upperCamelCase
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.BufferedSink
import okio.FileSystem

class GuestKotlinTarget(
  private val name: String,
  private val fileSystem: FileSystem,
  private val layout: ProjectLayout,
  private val testFunctions: List<TestFunction>,
) {
  suspend fun generate() {
    withContext(Dispatchers.IO + CoroutineName("GuestKotlinTarget")) {
      val path = layout.guestSrc / "dev/wasmo/brevity/integration/Guest${name.replaceFirstChar { it.uppercase() }}Implementation.kt"
      fileSystem.createDirectories(path.parent!!)
      fileSystem.write(path) { writeKotlin() }
    }
  }

  private fun BufferedSink.writeKotlin() {
    writeUtf8(
      """
      |@file:OptIn(
      |  ComponentModelInternalApi::class,
      |  ExperimentalStdlibApi::class,
      |  ExperimentalWasmInterop::class,
      |  UnsafeWasmMemoryApi::class,
      |)
      |package dev.wasmo.brevity.integration
      |
      |import kotlin.time.Duration.Companion.milliseconds
      |import kotlin.wasm.unsafe.ComponentModelInternalApi
      |import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
      |import kotlin.wasm.unsafe.componentModelRealloc
      |import kotlinx.coroutines.CompletableDeferred
      |import kotlinx.coroutines.Deferred
      |import kotlinx.coroutines.delay
      |import wit.brevity.testing.TestWorld
      |import wit.brevity.testing.guest
      |import wit.brevity.testing.host
      |
      |@EagerInitialization
      |val actuallyInitialize = run {
      |  TestWorld.guest = TestWorldGuestImplementation
      |}
      |
      |
      """.trimMargin(),
    )

    val parentToTestFunctions = testFunctions.groupBy { it.functionParent }
    for ((parent, childFunctions) in parentToTestFunctions) {
      when (parent) {
        FunctionParent.World -> {
          writeUtf8(
            """
            |object TestWorldGuestImplementation : TestWorld.Guest {
            |
            """.trimMargin(),
          )

          for (memberService in parentToTestFunctions.keys) {
            when (memberService) {
              FunctionParent.World -> {}
              FunctionParent.Interface -> {
                writeUtf8(
                  """
                  |  override val ${memberService.identifier.lowerCamelCase} = ${memberService.identifier.upperCamelCase}Implementation
                  |
                  """.trimMargin(),
                )
              }

              FunctionParent.Resource -> {
              }
            }
          }
        }

        FunctionParent.Interface -> {
          writeUtf8(
            """
            |object ${parent.identifier.upperCamelCase}Implementation : wit.brevity.testing.${parent.identifier.upperCamelCase} {
            |
            """.trimMargin(),
          )
        }

        FunctionParent.Resource -> {
        }
      }

      for (testFunction in childFunctions) {
        with(testFunction) {
          kotlinDeclareGuestFunctions()
        }
      }

      writeUtf8(
        """
        |}
        |
        |
        """.trimMargin(),
      )
    }
  }
}
