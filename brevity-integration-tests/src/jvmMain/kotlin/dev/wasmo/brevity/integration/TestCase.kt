package dev.wasmo.brevity.integration

import dev.wasmo.brevity.integration.PassingMechanism.PassAsParameter
import dev.wasmo.brevity.integration.PassingMechanism.PassAsReturnValue
import okio.Buffer
import okio.BufferedSink

data class TestCase(
  val function: TestFunction,
  val valueIndex: Int,
) {
  val passingMechanism: PassingMechanism
    get() = function.passingMechanism
  val callingMechanism: CallingMechanism
    get() = function.callingMechanism
  val type: SampleType
    get() = function.type

  val value: SampleValue
    get() = type.values[valueIndex]

  override fun toString() = "$function.$valueIndex"

  private fun BufferedSink.callAssertEquals(
    actual: BufferedSink.() -> Unit,
  ) {
    val valueSuffix = when {
      passingMechanism == PassAsReturnValue && type.compareAsString -> ".toString()"
      else -> ""
    }

    writeUtf8(
      """
      |  assertThat(
      |
      """.trimMargin(),
    )
    actual()

    val expected = when (passingMechanism) {
      is PassAsParameter -> valueIndex
      PassAsReturnValue -> value.kotlin
    }
    writeUtf8(
      """
      |$valueSuffix,
      |    "${this@TestCase}",
      |  ).isEqualTo($expected$valueSuffix)
      |
      """.trimMargin(),
    )
  }

  fun BufferedSink.kotlinCall(callTarget: String) {
    callMeasureTime {
      callAssertEquals(
        actual = {
          writeUtf8(
            """
            |    $callTarget.${function.kotlinFunctionName}(
            |
            """.trimMargin(),
          )

          for (i in 0 until function.padding) {
            writeUtf8(
              """
              |      p$i = 0,
              |
              """.trimMargin(),
            )
          }

          when (passingMechanism) {
            is PassAsParameter -> {
              writeUtf8(
                """
                |      v = ${value.kotlin},
                |    )
                """.trimMargin(),
              )
            }

            PassAsReturnValue -> {
              writeUtf8(
                """
                |      index = ${valueIndex},
                |    )
                """.trimMargin(),
              )
            }
          }
        },
      )
    }
  }

  private fun BufferedSink.callMeasureTime(
    block: BufferedSink.() -> Unit,
  ) {
    val blockContent = Buffer()
      .apply {
        block()
      }

    if ((callingMechanism as? CallingMechanism.Async)?.sleep != true) {
      writeAll(blockContent)
      return
    }

    writeUtf8(
      """
      |  assertThat(
      |    measureTime {
      |      ${blockContent.readUtf8().replace("\n", "\n    ")}
      |    }
      |  ).isGreaterThanOrEqualTo(100.milliseconds)
      |
      """.trimMargin(),
    )
  }
}
