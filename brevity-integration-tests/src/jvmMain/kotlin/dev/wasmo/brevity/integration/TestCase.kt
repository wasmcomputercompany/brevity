package dev.wasmo.brevity.integration

import dev.wasmo.brevity.Orientation
import dev.wasmo.brevity.integration.PassingMechanism.Parameter
import dev.wasmo.brevity.integration.PassingMechanism.Return
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
  val orientation: Orientation
    get() = function.orientation
  val type: SampleType
    get() = function.type

  val value: SampleValue
    get() = type.values[valueIndex]

  override fun toString() = "$function.$valueIndex"

  private fun BufferedSink.callAssertEquals(
    actual: String,
  ) {
    val (valuePrefix, valueSuffix) = when {
      passingMechanism == Return && type.compareAsString -> "(" to ").toString()"
      else -> "" to ""
    }

    val expected = when {
      passingMechanism is Parameter || orientation == Orientation.Import -> valueIndex
      else -> value.kotlin
    }

    writeUtf8(
      """
      |  assertThat(
      |    $valuePrefix${actual.replace("\n", "\n    ")}$valueSuffix,
      |    "${this@TestCase}",
      |  ).isEqualTo($valuePrefix$expected$valueSuffix)
      |
      """.trimMargin(),
    )
  }

  fun BufferedSink.kotlinCallAndAssert(callTarget: String) {
    callMeasureTime {
      val call = when (function.orientation) {
        Orientation.Import -> function.kotlinCallTrampoline(callTarget, valueIndex)
        Orientation.Export -> function.kotlinCall(callTarget, "$valueIndex", value.kotlin)
      }
      callAssertEquals(call)
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
