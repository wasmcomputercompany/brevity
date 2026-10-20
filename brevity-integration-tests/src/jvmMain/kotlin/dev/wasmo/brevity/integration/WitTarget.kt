package dev.wasmo.brevity.integration

import dev.wasmo.brevity.Keyword
import dev.wasmo.brevity.Orientation
import dev.wasmo.brevity.Orientation.Export
import dev.wasmo.brevity.Orientation.Import
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
  private val rawWitTypeDeclarations: String,
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

    // Group functions into their declaring types. This puts all world functions together but
    // separates interfaces and resources by orientation.
    val parentToTestFunctions = testFunctions.groupBy { it.functionParent }
    for ((parent, childFunctions) in parentToTestFunctions) {
      writeService(parent, childFunctions, parentToTestFunctions)
    }

    writeUtf8(
      """
      |interface types {
      |
      """.trimMargin(),
    )
    if (rawWitTypeDeclarations.isNotEmpty()) {
      writeUtf8(
        """
        |  ${rawWitTypeDeclarations.trim().replace("\n", "\n  ")}
        |
        """.trimMargin(),
      )
    }
    writeUtf8(
      """
      |}
      |
      """.trimMargin(),
    )
  }

  private fun BufferedSink.writeService(
    parent: FunctionParent,
    childFunctions: List<TestFunction>,
    parentToTestFunctions: Map<FunctionParent, List<TestFunction>>,
  ) {
    when (childFunctions.first().functionParent) {
      FunctionParent.WorldExport,
      FunctionParent.WorldImport,
      FunctionParent.InterfaceExport,
      FunctionParent.InterfaceImport,
        -> writeWorldOrInterface(parent, childFunctions, parentToTestFunctions)

      FunctionParent.ResourceExport, FunctionParent.ResourceImport -> {
        writeResource(parent, childFunctions)
      }
    }
  }

  private fun BufferedSink.writeWorldOrInterface(
    parent: FunctionParent,
    childFunctions: List<TestFunction>,
    parentToTestFunctions: Map<FunctionParent, List<TestFunction>>,
  ) {
    // We emit WorldExports in WorldImports.
    val childFunctionsToDeclare = when (parent) {
      FunctionParent.WorldExport -> childFunctions +
        parentToTestFunctions.entries.single { it.key == FunctionParent.WorldImport }.value

      FunctionParent.WorldImport -> return
      else -> childFunctions
    }

    val functionsString = Buffer().run {
      writeFunctions(childFunctionsToDeclare)
      readUtf8().trim()
    }

    writeUtf8(
      """
      |${parent.keyword} ${parent.identifier} {
      |  ${rawWitUseStatements.trim().replace("\n", "\n  ")}
      |
      |
      """.trimMargin(),
    )

    // The 'World' is the main parent.
    if (parent.isWorld) {
      writeUtf8(
        """
        |  import types;
        |
        """.trimMargin(),
      )
      for ((memberService, memberServiceFunctions) in parentToTestFunctions) {
        if (!memberService.isWorld) {
          writeUtf8(
            """
            |  ${memberService.orientation.keyword} ${memberService.serviceName};
            |
            """.trimMargin(),
          )
        }
        if (memberService.orientation == Import) {
          val trampolinesString = Buffer().run {
            writeTrampolines(memberServiceFunctions.filter { it.orientation == Import })
            readUtf8().trim()
          }
          writeUtf8(
            """
            |  ${trampolinesString.replace("\n", "\n  ")}
            |
            """.trimMargin(),
          )
        }
      }
      writeUtf8(
        """
        |
        |
        """.trimMargin(),
      )
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
      |interface ${parent.serviceName} {
      |  ${rawWitUseStatements.replace("\n", "\n  ").trim()}
      |
      |  create: func() -> ${parent.identifier};
      |
      |  resource ${parent.identifier} {
      |    ${functionsString.replace("\n", "\n    ")}
      |  }
      |}
      |
      |
      """.trimMargin(),
    )
  }

  private fun BufferedSink.writeFunctions(functions: List<TestFunction>) {
    for (function in functions) {
      with(function) {
        witDeclare()
      }
    }
  }

  private fun BufferedSink.writeTrampolines(functions: List<TestFunction>) {
    for (function in functions) {
      with(function) {
        witDeclareTrampoline()
      }
    }
  }

  val Orientation.keyword: Keyword
    get() = when (this) {
      Export -> Keyword.export
      Import -> Keyword.import
    }
}
