package dev.wasmo.brevity.kotlin.generator

import dev.wasmo.brevity.filterNamedWorlds
import dev.wasmo.brevity.collectNoIssuesOrThrow
import java.io.File
import kotlin.test.Test
import okio.FileSystem
import okio.Path.Companion.toPath

/** This dumps a `.kt` file for WASI 0.2.0, for manual inspection. */
class GenerateWasiP2KotlinTest {
  private val fileSystem = FileSystem.SYSTEM
  private val wasiPreview2 = "../submodules/wasi-p2/preview2".toPath()

  @Test
  fun generate() {
    val directories = mutableListOf(
      wasiPreview2 / "cli",
      wasiPreview2 / "clocks",
      wasiPreview2 / "filesystem",
      wasiPreview2 / "http",
      wasiPreview2 / "io",
      wasiPreview2 / "random",
      wasiPreview2 / "sockets",
    )

    val generator = collectNoIssuesOrThrow {
      WitBridgeGenerator.precompile(
        fileSystem = fileSystem,
        packageDirectories = directories,
        irFilter = {
          it.filterNamedWorlds(
            listOf(
              "wasi:cli/command",
              "wasi:http/proxy",
            ),
          )
        },
      )
    }!!

    val directory = File("build/GenerateWasiP2KotlinTest")
    directory.mkdirs()

    for (fileSpec in generator.api.generate()) {
      fileSpec.writeTo(directory)
    }
    for (fileSpec in generator.guest.generate()) {
      fileSpec.writeTo(directory)
    }
    for (fileSpec in generator.host.generate()) {
      fileSpec.writeTo(directory)
    }
  }
}
