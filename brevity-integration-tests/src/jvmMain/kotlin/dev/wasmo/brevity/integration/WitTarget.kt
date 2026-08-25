package dev.wasmo.brevity.integration

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.BufferedSink
import okio.FileSystem

class WitTarget(
  private val fileSystem: FileSystem,
  private val layout: ProjectLayout,
  private val types: List<SampleType>,
  private val rawWit: String,
) {
  suspend fun generate() {
    withContext(Dispatchers.IO + CoroutineName("WitTarget")) {
      fileSystem.createDirectories(layout.wit)
      fileSystem.write(layout.wit / "bridge-type-test.wit") { writeWit() }
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
      if (!type.mustAllocate) {
        writeUtf8(
          """
          |  export pass-as-parameter-${type.id}: func(v: ${type.witType}) -> s32;
          |
          """.trimMargin(),
        )
      }
      writeUtf8(
        """
        |  export pass-as-return-value-${type.id}: func(index: s32) -> ${type.witType};
        |
        """.trimMargin(),
      )
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
}
