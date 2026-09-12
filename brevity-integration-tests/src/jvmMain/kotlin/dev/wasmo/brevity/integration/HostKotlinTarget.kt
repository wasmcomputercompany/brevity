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
      val path =
        layout.hostSrc / "dev/wasmo/brevity/integration/Host${name.replaceFirstChar { it.uppercase() }}Main.kt"
      fileSystem.createDirectories(path.parent!!)
      fileSystem.write(path) { writeKotlin() }
    }
  }

  private fun BufferedSink.writeKotlin() {
    writeUtf8(
      """
      |package dev.wasmo.brevity.integration
      |
      |import assertk.assertThat
      |import assertk.assertions.isEqualTo
      |import dev.wasmo.brevity.integration.isEqualTo
      |import dev.wasmo.brevity.WasmInstance
      |import dev.wasmo.brevity.wasi.p1.RealWasiP1Host
      |import dev.wasmo.brevity.wasi.p2.RealWasiP2Host
      |import okio.Path.Companion.toPath
      |import wit.brevity.testing.BrevityTest
      |import wit.brevity.testing.World
      |import wit.wasi.cli.v0_2_0.World
      |import wit.wasi.v0_1.World
      |
      |fun main(vararg args: String) {
      |  val world = BrevityTest.World { }
      |  WasmInstance(
      |    path = args[0].toPath(),
      |    worlds = listOf(
      |      wit.wasi.v0_1.Wasi.World({ RealWasiP1Host() }),
      |      wit.wasi.cli.v0_2_0.Imports.World({ RealWasiP2Host() }),
      |      world,
      |    ),
      |  )
      |
      |
      """.trimMargin(),
    )
    for (type in types) {
      for ((index, value) in type.values.withIndex()) {
        callPassAsParameter(
          type = type,
          value = value,
          index = index,
        )
        callPassAsParameter(
          type = type,
          value = value,
          index = index,
          padding = 16,
        )
        callPassAsReturnValue(
          type = type,
          index = index,
          value = value,
        )
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

  private fun BufferedSink.callPassAsParameter(
    type: SampleType,
    value: SampleValue,
    index: Int,
    padding: Int = 0,
  ) {
    val paddingSuffix = when {
      padding > 0 -> "P$padding"
      else -> ""
    }

    writeUtf8(
      """
      |  assertThat(
      |    world.guest.passAsParameter${type.idUpperCamel}$paddingSuffix(
      |
      """.trimMargin(),
    )
    for (i in 0 until padding) {
      writeUtf8(
        """
        |    p$i = 0,
        |
        """.trimMargin(),
      )
    }
    writeUtf8(
      """
      |      v = ${value.kotlin},
      |    ),
      |    "${type.id}.$index.parameter.p$padding",
      |  ).isEqualTo($index)
      |
      """.trimMargin(),
    )
  }

  private fun BufferedSink.callPassAsReturnValue(
    type: SampleType,
    index: Int,
    value: SampleValue,
  ) {
    if (type.compareAsString) {
      writeUtf8(
        """
        |  assertThat(
        |    world.guest.passAsReturnValue${type.idUpperCamel}($index).toString(),
        |    "${type.id}.$index.return",
        |  ).isEqualTo((${value.kotlin}).toString())
        |
        |
        """.trimMargin(),
      )
    } else {
      writeUtf8(
        """
        |  assertThat(
        |    world.guest.passAsReturnValue${type.idUpperCamel}($index),
        |    "${type.id}.$index.return",
        |  ).isEqualTo(${value.kotlin})
        |
        |
        """.trimMargin(),
      )
    }
  }
}
