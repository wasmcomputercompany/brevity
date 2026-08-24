package dev.wasmo.brevity

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasMessage
import dev.wasmo.brevity.io.IoToplevelWitPackage
import dev.wasmo.brevity.io.IrMapper
import dev.wasmo.brevity.io.toWitFile
import dev.wasmo.brevity.ir.IrMapper
import dev.wasmo.brevity.ir.IrWorld
import kotlin.test.Test
import kotlin.test.assertFailsWith

class WorldFilterTest {
  private val commandLocation = Location("command.wit")
  private val importsLocation = Location("imports.wit")
  private val ioPackages = listOf(
    IoToplevelWitPackage(
      packageName = "wasi:cli@0.3.0".toPackageName(),
      files = listOf(
        """
        |package wasi:cli@0.3.0;
        |
        |world command {
        |}
        """.trimMargin().toWitFile(commandLocation),
        """
        |package wasi:cli@0.3.0;
        |
        |world imports {
        |}
        """.trimMargin().toWitFile(importsLocation),
      ),
    ),
  )

  @Test
  fun filterSuccess() = withIssueCollector {
    val irPackages = IrMapper(ioPackages).map()
    val commandWorld = irPackages.single().services.single {
      (it as? IrWorld)?.serviceName?.name?.name == "command"
    }

    assertThat(irPackages.filterNamedWorlds(listOf("command")).single().services)
      .containsExactly(commandWorld)
    assertThat(irPackages.filterNamedWorlds(listOf("wasi:cli/command")).single().services)
      .containsExactly(commandWorld)
    assertThat(irPackages.filterNamedWorlds(listOf("wasi:cli/command@0.3.0")).single().services)
      .containsExactly(commandWorld)
  }

  @Test
  fun filterDoesntMatch() = withIssueCollector {
    val irPackages = IrMapper(ioPackages).map()

    val e = assertFailsWith<IllegalArgumentException> {
      irPackages.filterNamedWorlds(listOf("wasi:command"))
    }
    assertThat(e).hasMessage("""
      |unexpected world name:
      |  wasi:command
      |not in acceptable set:
      |  command
      |  imports
      |  wasi:cli/command
      |  wasi:cli/command@0.3.0
      |  wasi:cli/imports
      |  wasi:cli/imports@0.3.0
      """.trimMargin())
  }
}
