package dev.wasmo.brevity.io

import assertk.assertThat
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import dev.wasmo.brevity.Documentation
import dev.wasmo.brevity.Location
import dev.wasmo.brevity.WitException
import dev.wasmo.brevity.collectNoIssuesOrThrow
import dev.wasmo.brevity.toPackageName
import kotlin.test.Test
import kotlin.test.assertFailsWith
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

class IoToplevelWitPackageReaderTest {
  @Test
  fun happyPath() = collectNoIssuesOrThrow {
    val directory = "/my-package".toPath()
    val fileSystem = FakeFileSystem()
    fileSystem.createDirectories(directory)
    fileSystem.write(directory / "command.wit") {
      writeUtf8(
        """
        |world command {
        |  export run;
        |  import exit;
        |}
        """.trimMargin(),
      )
    }
    fileSystem.write(directory / "exit.wit") {
      writeUtf8(
        """
        |/// command line interfaces!
        |package wasi:cli;
        |
        |interface exit {
        |  exit: func(status: result);
        |}
        """.trimMargin(),
      )
    }
    fileSystem.write(directory / "run.wit") {
      writeUtf8(
        """
        |interface run {
        |  run: func() -> result;
        |}
        """.trimMargin(),
      )
    }
    val packageReader = IoWitPackageReader(fileSystem)
    val ioWitPackage = packageReader.read(directory)
    val commandLocation = Location("my-package/command.wit")
    val exitLocation = Location("my-package/exit.wit")
    val runLocation = Location("my-package/run.wit")

    assertThat(ioWitPackage).isEqualTo(
      IoToplevelWitPackage(
        documentation = Documentation(" command line interfaces!"),
        packageName = "wasi:cli".toPackageName(),
        files = listOf(
          IoWitFile(
            items = listOf(
              IoWorld(
                location = commandLocation.at(1, 1),
                name = "command",
                imports = listOf(
                  IoExternalApi(
                    location = commandLocation.at(3, 3),
                    path = "exit",
                  ),
                ),
                exports = listOf(
                  IoExternalApi(
                    location = commandLocation.at(2, 3),
                    path = "run",
                  ),
                ),
              ),
            ),
            location = commandLocation,
          ),
          IoWitFile(
            packageDocumentation = Documentation(" command line interfaces!"),
            packageName = IoPackageNameElement("wasi:cli".toPackageName(), exitLocation.at(2, 1)),
            items = listOf(
              IoInterface(
                location = exitLocation.at(4, 1),
                name = "exit",
                items = listOf(
                  IoFunction(
                    location = exitLocation.at(5, 3),
                    name = "exit",
                    parameters = listOf(
                      IoParameter(
                        location = exitLocation.at(5, 14),
                        name = "status",
                        type = IoTypeName.Result(),
                      ),
                    ),
                  ),
                ),
              ),
            ),
            location = exitLocation,
          ),
          IoWitFile(
            items = listOf(
              IoInterface(
                location = runLocation.at(1, 1),
                name = "run",
                items = listOf(
                  IoFunction(
                    location = runLocation.at(2, 3),
                    name = "run",
                    returnType = IoTypeName.Result(),
                  ),
                ),
              ),
            ),
            location = runLocation,
          ),
        ),
      ),
    )
  }

  @Test
  fun `conflicting package names`() {
    val directory = "/my-package".toPath()
    val fileSystem = FakeFileSystem()
    fileSystem.createDirectories(directory)
    fileSystem.write(directory / "command.wit") {
      writeUtf8(
        """
        |package wasi:cli@1.0;
        """.trimMargin(),
      )
    }
    fileSystem.write(directory / "exit.wit") {
      writeUtf8(
        """
        |package wasi:cli@2.0;
        """.trimMargin(),
      )
    }

    val packageReader = IoWitPackageReader(fileSystem)
    val e = assertFailsWith<WitException> {
      collectNoIssuesOrThrow {
        packageReader.read(directory)
      }
    }
    assertThat(e).hasMessage(
      """
      |multiple different package names in directory:
      |  wasi:cli@1.0
      |  wasi:cli@2.0
      |  at /my-package""".trimMargin(),
    )
  }

  @Test
  fun `absent package name`() {
    val directory = "/my-package".toPath()
    val fileSystem = FakeFileSystem()
    fileSystem.createDirectories(directory)
    fileSystem.write(directory / "command.wit") {
      writeUtf8(
        """
        |interface command {
        |}
        """.trimMargin(),
      )
    }
    fileSystem.write(directory / "exit.wit") {
      writeUtf8(
        """
        |interface exit {
        |}
        """.trimMargin(),
      )
    }

    val packageReader = IoWitPackageReader(fileSystem)
    val e = assertFailsWith<WitException> {
      collectNoIssuesOrThrow {
        packageReader.read(directory)
      }
    }
    assertThat(e).hasMessage("no package declaration in directory at /my-package")
  }
}
