package dev.wasmo.brevity.integration

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.BufferedSink
import okio.FileSystem

class WitTarget(
  private val name: String,
  private val fileSystem: FileSystem,
  private val layout: ProjectLayout,
  private val types: List<SampleType>,
  private val rawWit: String,
) {
  suspend fun generate() {
    withContext(Dispatchers.IO + CoroutineName("WitTarget")) {
      fileSystem.createDirectories(layout.wit)
      fileSystem.write(layout.wit / "bridge-type-$name-test.wit") { writeWit() }
    }
  }

  private fun BufferedSink.writeWit() {
    writeUtf8(
      """
      |package brevity:testing;
      |
      |world brevity-test {
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
        async = false,
      )
      passAsReturnValue(
        type = type,
        async = false,
      )
      if (type.async) {
        passAsParameter(
          type = type,
          async = true,
        )
        passAsParameter(
          type = type,
          padding = 4,
          async = true,
        )
        passAsReturnValue(
          type = type,
          async = true,
        )
      }
      if (type.futures) {
        asyncFutureReturnValue(type = type)
        futureReturnValue(type = type)
      }
    }
    writeUtf8(
      """
      |$rawWit
      |
      |}
      |
      """.trimMargin(),
    )
  }

  private fun BufferedSink.passAsParameter(
    type: SampleType,
    padding: Int = 0,
    async: Boolean = false,
  ) {
    val paddingSuffix = when {
      padding > 0 -> "-p$padding"
      else -> ""
    }

    val asyncSuffix = when {
      async -> "-async"
      else -> ""
    }

    val modifiers = when {
      async -> " async"
      else -> ""
    }

    writeUtf8(
      """
      |  export pass-as-parameter-${type.id}$paddingSuffix$asyncSuffix:$modifiers func(
      |
      """.trimMargin(),
    )
    for (i in 0 until padding) {
      writeUtf8(
        """
        |    p$i: s32,
        |
        """.trimMargin(),
      )
    }
    writeUtf8(
      """
      |    v: ${type.witType},
      |  ) -> s32;
      |
      """.trimMargin(),
    )
  }

  private fun BufferedSink.passAsReturnValue(
    type: SampleType,
    async: Boolean = false,
  ) {
    val asyncSuffix = when {
      async -> "-async"
      else -> ""
    }

    val modifiers = when {
      async -> " async"
      else -> ""
    }

    writeUtf8(
      """
      |  export pass-as-return-value-${type.id}$asyncSuffix:$modifiers func(
      |    index: s32,
      |  ) -> ${type.witType};
      |
      """.trimMargin(),
    )
  }

  private fun BufferedSink.asyncFutureReturnValue(type: SampleType) {
    writeUtf8(
      """
      |  export async-future-return-value-${type.id}: async func(
      |    index: s32,
      |  ) -> future<${type.witType}>;
      |
      """.trimMargin(),
    )
  }

  private fun BufferedSink.futureReturnValue(type: SampleType) {
    writeUtf8(
      """
      |  export future-return-value-${type.id}: func(
      |    index: s32,
      |  ) -> future<${type.witType}>;
      |
      """.trimMargin(),
    )
  }
}
