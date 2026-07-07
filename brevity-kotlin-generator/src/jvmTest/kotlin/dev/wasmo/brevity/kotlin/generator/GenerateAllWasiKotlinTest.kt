package dev.wasmo.brevity.kotlin.generator

import dev.wasmo.brevity.DeclarationIndex
import dev.wasmo.brevity.RoleTracker
import dev.wasmo.brevity.io.IoWitPackageReader
import dev.wasmo.brevity.ir.IrMapper
import java.io.File
import kotlin.test.Test
import okio.FileSystem
import okio.Path.Companion.toPath

/** This dumps a `.kt` file for all the WASI proposals, for manual inspection. */
class GenerateAllWasiKotlinTest {
  private val fileSystem = FileSystem.SYSTEM
  private val wasiProposals = "../submodules/WASI/proposals".toPath()

  @Test
  fun generate() {
    val directories = mutableListOf(
      wasiProposals / "cli/wit",
      wasiProposals / "clocks/wit",
      wasiProposals / "filesystem/wit",
      wasiProposals / "http/wit",
      wasiProposals / "random/wit",
      wasiProposals / "sockets/wit",
    )

    val packageReader = IoWitPackageReader(fileSystem)
    val ioWitPackages = directories.map {
      packageReader.read(it)
    }

    val irPackages = IrMapper(ioWitPackages).map()

    val directory = File("build/GenerateAllWasiKotlinTest")
    directory.mkdirs()

    val declarationIndex = DeclarationIndex(irPackages)
    val roleTracker = RoleTracker(declarationIndex, irPackages)

    for (fileSpec in ApiGenerator(irPackages).generate()) {
      fileSpec.writeTo(directory)
    }
    for (fileSpec in GuestGenerator(declarationIndex, roleTracker, irPackages).generate()) {
      fileSpec.writeTo(directory)
    }
    for (fileSpec in HostGenerator(declarationIndex, roleTracker, irPackages).generate()) {
      fileSpec.writeTo(directory)
    }
  }
}
