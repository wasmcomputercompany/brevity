package dev.wasmo.brevity

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasMessage
import dev.wasmo.brevity.io.IoToplevelWitPackage
import dev.wasmo.brevity.io.toWitFile
import dev.wasmo.brevity.ir.IrMapper
import dev.wasmo.brevity.ir.IrWorld
import kotlin.test.Test
import kotlin.test.assertFailsWith
import okio.Path.Companion.toPath

class WorldFilterTest {
  private val ioPackages = listOf(
    IoToplevelWitPackage(
      packageName = "wasi:cli@0.3.0".toPackageName(),
      files = mapOf(
        "command.wit".toPath() to """
          |package wasi:cli@0.3.0;
          |
          |world command {
          |}
          """.trimMargin().toWitFile(),
        "imports.wit".toPath() to """
          |package wasi:cli@0.3.0;
          |
          |world imports {
          |}
          """.trimMargin().toWitFile(),
      ),
    ),
  )

  @Test
  fun filterSuccess() {
    val irPackages = IrMapper(ioPackages).map()
    val commandWorld = irPackages.single().items.single {
      (it as? IrWorld)?.name?.name == "command"
    }

    assertThat(irPackages.filterNamedWorlds(listOf("command")).single().items)
      .containsExactly(commandWorld)
    assertThat(irPackages.filterNamedWorlds(listOf("wasi:cli/command")).single().items)
      .containsExactly(commandWorld)
    assertThat(irPackages.filterNamedWorlds(listOf("wasi:cli/command@0.3.0")).single().items)
      .containsExactly(commandWorld)
  }

  @Test
  fun filterDoesntMatch() {
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
