package dev.wasmo.brevity.integration

import dev.wasmo.brevity.Orientation
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
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
      writeService(parent, childFunctions, parentToTestFunctions)
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

  private fun BufferedSink.writeService(
    parent: FunctionParent,
    childFunctions: List<TestFunction>,
    parentToTestFunctions: Map<FunctionParent, List<TestFunction>>,
  ) {
    when (parent) {
      FunctionParent.World, FunctionParent.Interface -> {
        writeWorldOrInterface(parent, childFunctions, parentToTestFunctions)
      }

      FunctionParent.Resource -> {
        writeResource(parent, childFunctions)
      }
    }
  }

  private fun BufferedSink.writeWorldOrInterface(
    parent: FunctionParent,
    childFunctions: List<TestFunction>,
    parentToTestFunctions: Map<FunctionParent, List<TestFunction>>,
  ) {
    val functionsString = Buffer().run {
      writeFunctions(childFunctions)
      readUtf8().trim()
    }

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

          FunctionParent.Resource -> {
            writeUtf8(
              """
              |  export exported-${memberService.identifier};
              |
              """.trimMargin(),
            )
          }
        }
      }
    }

    writeUtf8(
      """
      |  ${functionsString.replace("\n", "\n  ")}
      |}
      |
      |
      """.trimMargin(),
    )
  }

  private fun BufferedSink.writeResource(
    parent: FunctionParent,
    childFunctions: List<TestFunction>,
  ) {
    val functionsString = Buffer().run {
      writeFunctions(childFunctions)
      readUtf8().trim()
    }
    writeUtf8(
      """
      |interface exported-${parent.identifier} {
      |  ${rawWitUseStatements.replace("\n", "\n  ").trim()}
      |
      |  create: func() -> ${parent.identifier};
      |
      |  resource ${parent.identifier} {
      |    ${functionsString.replace("\n", "\n    ")}
      |  }
      |}
      |
      """.trimMargin(),
    )
  }

  private fun BufferedSink.writeFunctions(functions: List<TestFunction>) {
    for (function in functions) {
      with(function) {
        witDeclare()
        if (function.orientation == Orientation.Import) {
          witDeclareTrampoline()
        }
      }
    }
  }
}
