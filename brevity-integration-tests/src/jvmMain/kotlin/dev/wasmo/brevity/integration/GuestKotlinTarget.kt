package dev.wasmo.brevity.integration

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.BufferedSink
import okio.FileSystem

class GuestKotlinTarget(
  private val name: String,
  private val fileSystem: FileSystem,
  private val layout: ProjectLayout,
  private val types: List<SampleType>,
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
      |import wit.brevity.testing.BrevityTest
      |import wit.brevity.testing.guest
      |import wit.brevity.testing.host
      |
      |@EagerInitialization
      |val actuallyInitialize = run {
      |  BrevityTest.guest = GuestImplementation
      |}
      |
      |object GuestImplementation : BrevityTest.Guest {
      |
      """.trimMargin(),
    )
    for (testFunction in types.testFunctions) {
      with(testFunction) {
        kotlinDeclareGuestFunctions()
      }
    }
    writeUtf8(
      """
      |}
      |
      """.trimMargin(),
    )
  }
}
