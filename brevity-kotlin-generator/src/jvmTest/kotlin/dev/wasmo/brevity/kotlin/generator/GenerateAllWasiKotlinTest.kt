package dev.wasmo.brevity.kotlin.generator

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
    val ktMapper = KtMapper()

    val directory = File("build/GenerateAllWasiKotlinTest")
    directory.mkdirs()

    val ktServices = ktMapper.map(irPackages)
    val worldIndex = WorldIndex(ktServices)

    for (fileSpec in ApiGenerator(ktServices).generate()) {
      fileSpec.writeTo(directory)
    }
    for (fileSpec in GuestGenerator(worldIndex, ktServices).generate()) {
      fileSpec.writeTo(directory)
    }
    for (fileSpec in HostGenerator(worldIndex, ktServices).generate()) {
      fileSpec.writeTo(directory)
    }
  }
}
