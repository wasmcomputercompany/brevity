package dev.wasmo.brevity.integration

import dev.wasmo.brevity.Orientation
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.BufferedSink
import okio.FileSystem

class WitTarget(
  private val name: String,
  private val fileSystem: FileSystem,
  private val layout: ProjectLayout,
  private val rawWit: String,
  private val rawWitUseStatements: String,
  private val testFunctions: List<TestFunction>,
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
      |
      """.trimMargin(),
    )

    val parentToTestFunctions = testFunctions.groupBy { it.functionParent }
    for ((parent, childFunctions) in parentToTestFunctions) {
      writeUtf8(
        """
        |${parent.keyword} ${parent.identifier} {
        |  ${rawWitUseStatements.replace("\n", "\n  ")}
        |
        """.trimMargin(),
      )

      // The 'World' is the main parent.
      if (parent == FunctionParent.World) {
        for (memberService in parentToTestFunctions.keys) {
          when (memberService) {
            FunctionParent.World -> {}
            FunctionParent.Interface -> {
              writeUtf8(
                """
                |  import ${memberService.identifier};
                |  export ${memberService.identifier};
                |
                |
                """.trimMargin(),
              )
            }
          }
        }
      }

      for ((_, overloads) in childFunctions.groupBy { it.identifier }) {
        with(overloads.first()) {
          witDeclare()
        }
        val import = overloads.firstOrNull { it.orientation == Orientation.Import }
        if (import != null) {
          with(import) {
            witDeclareTrampoline()
          }
        }
      }

      writeUtf8(
        """
        |}
        |
        |
        """.trimMargin(),
      )
    }

    if (rawWit.isNotEmpty()) {
      writeUtf8(
        """
        |$rawWit
        |
        """.trimMargin(),
      )
    }
  }
}
