package dev.wasmo.brevity.integration

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
  private val types: List<SampleType>,
  private val sleep: Boolean,
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
      |fun main(vararg args: String) = runTest {
      |  val world = BrevityTest.World { }
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
        if (type.async) {
          callPassAsParameter(
            type = type,
            index = index,
            value = value,
            async = true,
          )
          callPassAsParameter(
            type = type,
            index = index,
            padding = 4,
            value = value,
            async = true,
          )
          callPassAsReturnValue(
            type = type,
            index = index,
            value = value,
            async = true,
          )
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

  private fun BufferedSink.callPassAsParameter(
    type: SampleType,
    value: SampleValue,
    index: Int,
    padding: Int = 0,
    async: Boolean = false,
  ) {
    maybeWriteSleep(async) {
      val paddingSuffix = when {
        padding > 0 -> "P$padding"
        else -> ""
      }
      val asyncSuffix = when {
        async -> "Async"
        else -> ""
      }

      writeUtf8(
        """
      |  assertThat(
      |    world.guest.passAsParameter${type.idUpperCamel}$paddingSuffix$asyncSuffix(
      |
      """.trimMargin(),
      )
      for (i in 0 until padding) {
        writeUtf8(
          """
        |      p$i = 0,
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
  }

  private fun BufferedSink.callPassAsReturnValue(
    type: SampleType,
    index: Int,
    value: SampleValue,
    async: Boolean = false,
  ) {
    maybeWriteSleep(async) {
      val asyncSuffix = when {
        async -> "Async"
        else -> ""
      }

      if (type.compareAsString) {
        writeUtf8(
          """
        |  assertThat(
        |    world.guest.passAsReturnValue${type.idUpperCamel}$asyncSuffix($index).toString(),
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
        |    world.guest.passAsReturnValue${type.idUpperCamel}$asyncSuffix($index),
        |    "${type.id}.$index.return",
        |  ).isEqualTo(${value.kotlin})
        |
        |
        """.trimMargin(),
        )
      }
    }
  }

  private fun BufferedSink.maybeWriteSleep(
    async: Boolean,
    block: BufferedSink.() -> Unit,
  ) {
    val blockContent = Buffer()
      .apply {
        block()
      }

    if (!async || !sleep) {
      writeAll(blockContent)
      return
    }

    writeUtf8(
      """
      |  assertThat(
      |    measureTime {
      |      ${blockContent.readUtf8().replace("\n", "\n  ")}
      |    }
      |  ).isGreaterThanOrEqualTo(100.milliseconds)
      |
      """.trimMargin(),
    )
  }
}
