package dev.wasmo.brevity.kotlin.generator

import dev.wasmo.brevity.collectNoIssuesOrThrow
import dev.wasmo.brevity.filterNamedWorlds
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

    val directory = "build/GenerateWasiP2KotlinTest".toPath()
    fileSystem.createDirectories(directory)

    val projectSpec = generator.generate()
    projectSpec.writeTo(
      fileSystem = fileSystem,
      commonMain = directory,
      wasmWasiMain = directory,
      jvmMain = directory,
    )
  }
}
