package dev.wasmo.brevity.io

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.wasmo.brevity.Location
import dev.wasmo.brevity.WitException
import dev.wasmo.brevity.ir.IrMapper
import kotlin.test.Test
import kotlin.test.fail
import okio.FileSystem
import okio.Path.Companion.toPath

class ReadWasiMainTest {
  private val fileSystem = FileSystem.SYSTEM
  private val wasiMainProposals = "../submodules/wasi-main/proposals".toPath()

  @Test
  fun `parse all files`() {
    var witFileCount = 0
    for (path in fileSystem.listRecursively(wasiMainProposals)) {
      if (!path.name.endsWith(".wit")) continue

      val witContent = fileSystem.read(path) {
        readUtf8()
      }

      try {
        witContent.toWitFile(Location(path))
      } catch (e: WitException) {
        fail("decoding $path failed at ${e.location}: ${e.issue.description}")
      }

      witFileCount++
    }

    // Confirm we successfully decoded a reasonable number of files. If this fails after updating
    // the WASI submodule, it's probably correct to change this value.
    //
    // But don't change it to 0, that means our paths are out of date.
    assertThat(witFileCount).isEqualTo(24)
  }

  @Test
  fun `map all files`() {
    val directories = mutableListOf(
      wasiMainProposals / "cli/wit",
      wasiMainProposals / "clocks/wit",
      wasiMainProposals / "filesystem/wit",
      wasiMainProposals / "http/wit",
      wasiMainProposals / "random/wit",
      wasiMainProposals / "sockets/wit",
    )

    val ioWitPackages = directories.map {
      IoWitPackageReader(fileSystem).read(it)
    }

    IrMapper(ioWitPackages).map()
  }
}
