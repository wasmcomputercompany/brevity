package dev.wasmo.brevity.kotlin.generator

import dev.wasmo.brevity.filterNamedWorlds
import dev.wasmo.brevity.collectNoIssuesOrThrow
import java.io.File
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

    val directory = File("build/GenerateWasiMainKotlinTest")
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
