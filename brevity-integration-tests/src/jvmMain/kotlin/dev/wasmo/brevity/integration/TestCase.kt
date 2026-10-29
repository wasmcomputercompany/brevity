package dev.wasmo.brevity.integration

import dev.wasmo.brevity.Orientation
import dev.wasmo.brevity.integration.PassingMechanism.Parameter
import dev.wasmo.brevity.integration.PassingMechanism.Return
import dev.wasmo.brevity.kotlin.generator.lowerCamelCase
import okio.Buffer
import okio.BufferedSink

data class TestCase(
  val function: TestFunction,
  val valueIndex: Int,
  val delayMillis: ULong,
) {
  val passingMechanism: PassingMechanism
    get() = function.passingMechanism
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
      |assertThat(
      |  $valuePrefix${actual.replace("\n", "\n    ")}$valueSuffix,
      |  "${this@TestCase}",
      |).isEqualTo($valuePrefix$expected$valueSuffix)
      |
      """.trimMargin(),
    )
  }

  fun BufferedSink.kotlinCallAndAssert() {
    val callTarget = when (function.functionParent) {
      FunctionParent.InterfaceExport ->
        "world.guest.${function.functionParent.serviceName.lowerCamelCase}"

      FunctionParent.ResourceExport ->
        "world.guest.${function.functionParent.serviceName.lowerCamelCase}.create()"

      else -> "world.guest"
    }

    callMeasureTestTime {
      val call = when (function.orientation) {
        Orientation.Import -> function.kotlinCallTrampoline(callTarget, valueIndex, delayMillis)
        Orientation.Export -> function.kotlinCall(
          callTarget,
          "$valueIndex",
          value.kotlin,
          "${delayMillis}UL",
        )
      }
      callAssertEquals(call)
    }
  }

  private fun BufferedSink.callMeasureTestTime(
    block: BufferedSink.() -> Unit,
  ) {
    val blockContent = Buffer().run {
      block()
      readUtf8().trim()
    }

    if (delayMillis > 0UL && function.callingMechanism == CallingMechanism.Async) {
      writeUtf8(
        """
        |  assertThat(
        |    measureTestTime {
        |      ${blockContent.replace("\n", "\n      ")}
        |    }
        |  ).isEqualTo(${delayMillis}L.milliseconds)
        |
        """.trimMargin(),
      )
    } else {
      writeUtf8(
        """
        |  ${blockContent.replace("\n", "\n  ")}
        |
        """.trimMargin(),
      )
    }
  }
}
