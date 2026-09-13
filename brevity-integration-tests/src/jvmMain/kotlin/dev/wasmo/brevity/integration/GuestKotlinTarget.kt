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
      |import kotlin.wasm.unsafe.ComponentModelInternalApi
      |import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
      |import kotlin.wasm.unsafe.componentModelRealloc
      |import wit.brevity.testing.BrevityTest
      |import wit.brevity.testing.guest
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
      passAsParameter(
        type = type,
      )
      passAsParameter(
        type = type,
        padding = 16,
      )
      passAsReturnValue(type)
    }
    writeUtf8(
      """
      |}
      |
      """.trimMargin(),
    )
  }

  private fun BufferedSink.passAsParameter(
    type: SampleType,
    padding: Int = 0,
  ) {
    val paddingSuffix = when {
      padding > 0 -> "P$padding"
      else -> ""
    }
    writeUtf8(
      """
      |  override fun passAsParameter${type.idUpperCamel}$paddingSuffix(
      |
      """.trimMargin(),
    )
    for (i in 0 until padding) {
      writeUtf8(
        """
        |    p$i: Int,
        |
        """.trimMargin(),
      )
    }
    writeUtf8(
      """
      |    v: ${type.kotlinType},
      |  ): Int {
      |
      """.trimMargin(),
    )
    if (type.compareAsString) {
      writeUtf8(
        """
        |    val v_str = v.toString()
        |
        """.trimMargin(),
      )
      for ((index, value) in type.values.withIndex()) {
        writeUtf8(
          """
          |    if (v_str == (${value.kotlin}).toString()) { return $index }
          |
          """.trimMargin(),
        )
      }
    } else {
      for ((index, value) in type.values.withIndex()) {
        if (type.kotlinEqualityMethod == null) {
          writeUtf8(
            """
            |    if (v == ${value.kotlin}) { return $index }
            |
            """.trimMargin(),
          )
        } else {
          writeUtf8(
            """
            |    if (v.${type.kotlinEqualityMethod}(${value.kotlin})) { return $index }
            |
            """.trimMargin(),
          )
        }
      }
    }
    writeUtf8(
      """
      |    return -1
      |  }
      |
      """.trimMargin(),
    )
  }

  private fun BufferedSink.passAsReturnValue(type: SampleType) {
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
}
