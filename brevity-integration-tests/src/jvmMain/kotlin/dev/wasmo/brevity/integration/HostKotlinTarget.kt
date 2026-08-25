package dev.wasmo.brevity.integration

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.BufferedSink
import okio.FileSystem

class HostKotlinTarget(
  private val fileSystem: FileSystem,
  private val layout: ProjectLayout,
  private val types: List<SampleType>,
) {
  suspend fun generate() {
    withContext(Dispatchers.IO + CoroutineName("HostKotlinTarget")) {
      val path = layout.hostSrc / "dev/wasmo/brevity/integration/HostMain.kt"
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
      |import brevity.wasi.p2.RealWasiP2Host
      |import dev.wasmo.brevity.WasmInstance
      |import okio.Path.Companion.toPath
      |import wit.brevity.testing.BrevityTest
      |import wit.brevity.testing.World
      |import wit.wasi.cli.v0_2_0.World
      |import wit.wasi.v0_1.SystemWasiP1
      |import wit.wasi.v0_1.World
      |
      |fun main(vararg args: String) {
      |  val world = BrevityTest.World { }
      |  WasmInstance(
      |    path = args[0].toPath(),
      |    worlds = listOf(
      |      wit.wasi.v0_1.Wasi.World({ SystemWasiP1 }),
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
        if (!type.mustAllocate) {
          writeUtf8(
            """
            |  assertThat(
            |    world.guest.passAsParameter${type.idUpperCamel}(${value.kotlin}),
            |    "${type.id}.$index.parameter",
            |  ).isEqualTo($index)
            |
            """.trimMargin(),
          )
        }

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
    writeUtf8(
      """
      |}
      |
      """.trimMargin(),
    )
  }
}
