package dev.wasmo.brevity.integration

import dev.wasmo.brevity.Orientation
import dev.wasmo.brevity.Orientation.Import
import dev.wasmo.brevity.kotlin.generator.lowerCamelCase
import dev.wasmo.brevity.kotlin.generator.upperCamelCase
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
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

    val parentToTestFunctions = this@GuestKotlinTarget.testFunctions.groupBy { it.functionParent }
    for ((parent, testFunctions) in parentToTestFunctions) {
      val functionsString = Buffer().run {
        for (testFunction in testFunctions.filter { it.orientation == Orientation.Export }) {
          with(testFunction) {
            kotlinDeclare()
          }
        }
        readUtf8().trim()
      }

      when (parent) {
        FunctionParent.WorldExport -> {
          writeUtf8(
            """
            |object TestWorldGuestImplementation : TestWorld.Guest {
            |
            """.trimMargin(),
          )

          for (memberService in parentToTestFunctions.keys) {
            when (memberService) {
              FunctionParent.InterfaceExport, FunctionParent.ResourceExport -> {
                writeUtf8(
                  """
                  |  override val ${memberService.serviceName.lowerCamelCase} = ${memberService.serviceName.upperCamelCase}Implementation
                  |
                  """.trimMargin(),
                )
              }

              else -> {
              }
            }
          }

          val trampolinesString = Buffer().run {
            val trampolineFunctions = parentToTestFunctions.flatMap { it.value }
              .filter { it.orientation == Import }
            for (trampolineFunction in trampolineFunctions) {
              with(trampolineFunction) {
                kotlinDeclareTrampoline()
              }
            }
            readUtf8().trim()
          }

          writeUtf8(
            """
            |  ${functionsString.replace("\n", "\n  ")}
            |
            |  ${trampolinesString.replace("\n", "\n  ")}
            |}
            |
            |
            """.trimMargin(),
          )
        }

        FunctionParent.InterfaceExport -> {
          writeUtf8(
            """
            |object ${parent.serviceName.upperCamelCase}Implementation : wit.brevity.testing.${parent.serviceName.upperCamelCase} {
            |  ${functionsString.replace("\n", "\n  ")}
            |}
            |
            |
            """.trimMargin(),
          )
        }

        FunctionParent.ResourceExport -> {
          writeUtf8(
            """
            |object ${parent.serviceName.upperCamelCase}Implementation : wit.brevity.testing.${parent.serviceName.upperCamelCase} {
            |  override fun create() = ${parent.identifier.upperCamelCase}Implementation
            |}
            |
            |object ${parent.identifier.upperCamelCase}Implementation : wit.brevity.testing.${parent.serviceName.upperCamelCase}.${parent.identifier.upperCamelCase}() {
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
  }
}
