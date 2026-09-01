package dev.wasmo.brevity.kotlin.generator

import dev.wasmo.brevity.collectNoIssuesOrThrow
import dev.wasmo.brevity.filterNamedWorlds
import kotlin.test.Test
import okio.FileSystem
import okio.Path.Companion.toPath

/** This dumps a `.kt` file for all the WASI proposals, for manual inspection. */
class GenerateWasiMainKotlinTest {
  private val fileSystem = FileSystem.SYSTEM
  private val wasiMainProposals = "../submodules/wasi-p3/proposals".toPath()

  @Test
  fun generate() {
    val directories = mutableListOf(
      wasiMainProposals / "cli/wit",
      wasiMainProposals / "clocks/wit",
      wasiMainProposals / "filesystem/wit",
      wasiMainProposals / "http/wit",
      wasiMainProposals / "random/wit",
      wasiMainProposals / "sockets/wit",
    )

    val generator = collectNoIssuesOrThrow {
      WitBridgeGenerator.precompile(
        fileSystem = fileSystem,
        packageDirectories = directories,
        irFilter = { it.filterNamedWorlds(listOf("wasi:http/service@0.3.1")) },
      )
    }!!

    val directory = "build/GenerateWasiMainKotlinTest".toPath()
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
