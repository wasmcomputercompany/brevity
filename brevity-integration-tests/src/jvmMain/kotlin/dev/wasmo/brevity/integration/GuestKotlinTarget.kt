package dev.wasmo.brevity.integration

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.BufferedSink
import okio.FileSystem

class GuestKotlinTarget(
  private val fileSystem: FileSystem,
  private val layout: ProjectLayout,
  private val types: List<SampleType>,
) {
  suspend fun generate() {
    withContext(Dispatchers.IO + CoroutineName("GuestKotlinTarget")) {
      val path = layout.guestSrc / "dev/wasmo/brevity/integration/GuestImplementation.kt"
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
      |import kotlin.wasm.unsafe.ComponentModelInternalApi
      |import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
      |import kotlin.wasm.unsafe.componentModelRealloc
      |import wit.brevity.testing.BrevityTest
      |import wit.brevity.testing.guest
      |
      |@WasmExport(name = "cabi_realloc")
      |fun cabi_realloc(ptr: Int, oldSize: Int, align: Int, newSize: Int): Int =
      |  componentModelRealloc(ptr, oldSize, newSize)
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
    for (type in types) {
      writeUtf8(
        """
        |  override fun passAsParameter${type.idUpperCamel}(v: ${type.kotlinType}): Int {
        |    return when (v) {
        |
        """.trimMargin(),
      )
      for ((index, value) in type.values.withIndex()) {
        writeUtf8(
          """
          |      ${value.kotlin} -> $index
          |
          """.trimMargin(),
        )
      }
      writeUtf8(
        $$"""
        |      else -> -1
        |    }
        |  }
        |
        """.trimMargin(),
      )

      writeUtf8(
        """
        |  override fun passAsReturnValue${type.idUpperCamel}(index: Int): ${type.kotlinType} {
        |    return when (index) {
        |
        """.trimMargin(),
      )
      for ((index, value) in type.values.withIndex()) {
        writeUtf8(
          """
          |      $index -> ${value.kotlin}
          |
          """.trimMargin(),
        )
      }
      writeUtf8(
        $$"""
        |      else -> error("unexpected index: $index")
        |    }
        |  }
        |
        """.trimMargin(),
      )
    }
    writeUtf8(
      """
      |}
      |
      """.trimMargin(),
    )
  }
}
