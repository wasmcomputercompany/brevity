package dev.wasmo.brevity.kotlin.generator

import dev.wasmo.brevity.DeclarationIndex
import dev.wasmo.brevity.RoleTracker
import dev.wasmo.brevity.io.IoWitPackageReader
import dev.wasmo.brevity.io.IrMapper
import dev.wasmo.brevity.ir.IrMapper
import dev.wasmo.brevity.kotlin.encoders.EncoderFactory
import java.io.File
import kotlin.test.Test
import okio.FileSystem
import okio.Path.Companion.toPath

/** This dumps a `.kt` file for all the WASI proposals, for manual inspection. */
class GenerateWasiMainKotlinTest {
  private val fileSystem = FileSystem.SYSTEM
  private val wasiMainProposals = "../submodules/wasi-main/proposals".toPath()

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

    val packageReader = IoWitPackageReader(fileSystem)
    val ioWitPackages = directories.map {
      packageReader.read(it)
    }

    val irPackages = IrMapper(ioWitPackages).map()

    val directory = File("build/GenerateWasiMainKotlinTest")
    directory.mkdirs()

    val declarationIndex = DeclarationIndex(irPackages)
    val roleTracker = RoleTracker(declarationIndex, irPackages)
    val encoderFactory = EncoderFactory(declarationIndex)
    val encodersGenerator = EncodersGenerator(encoderFactory, declarationIndex, roleTracker, irPackages)
    val guestGenerator = GuestGenerator(encoderFactory, declarationIndex, encodersGenerator, roleTracker, irPackages)
    val hostGenerator = HostGenerator(encoderFactory, declarationIndex, encodersGenerator, roleTracker, irPackages)

    for (fileSpec in ApiGenerator(irPackages).generate()) {
      fileSpec.writeTo(directory)
    }
    for (fileSpec in guestGenerator.generate()) {
      fileSpec.writeTo(directory)
    }
    for (fileSpec in hostGenerator.generate()) {
      fileSpec.writeTo(directory)
    }
  }
}
