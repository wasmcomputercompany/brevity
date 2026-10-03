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

    for (testFunction in types.testFunctions) {
      with(testFunction) {
        witDeclare()
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
}
