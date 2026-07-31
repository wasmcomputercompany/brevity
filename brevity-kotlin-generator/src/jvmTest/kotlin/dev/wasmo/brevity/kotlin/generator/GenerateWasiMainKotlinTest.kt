package dev.wasmo.brevity.kotlin.generator

import dev.wasmo.brevity.DeclarationIndex
import dev.wasmo.brevity.RoleTracker
import dev.wasmo.brevity.filterNamedWorlds
import dev.wasmo.brevity.io.IoWitPackageReader
import dev.wasmo.brevity.io.IrMapper
import dev.wasmo.brevity.kotlin.code.GuestPlatform
import dev.wasmo.brevity.kotlin.code.HostPlatform
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

    val allIrPackages = IrMapper(ioWitPackages).map()
    val irPackages = allIrPackages.filterNamedWorlds(
      listOf(
        "wasi:http/service@0.3.0",
      )
    )

    val directory = File("build/GenerateWasiMainKotlinTest")
    directory.mkdirs()

    val declarationIndex = DeclarationIndex(irPackages)
    val roleTracker = RoleTracker(declarationIndex, irPackages)
    val encoderFactory = EncoderFactory(declarationIndex)
    val guestGenerator = GuestGenerator(
      encoderFactory = encoderFactory,
      declarationIndex = declarationIndex,
      declaredTypeEncodersGenerator = DeclaredTypeEncodersGenerator(encoderFactory, GuestPlatform),
      roleTracker = roleTracker,
      packages = irPackages
    )
    val hostGenerator = HostGenerator(
      encoderFactory = encoderFactory,
      declarationIndex = declarationIndex,
      declaredTypeEncodersGenerator = DeclaredTypeEncodersGenerator(encoderFactory, HostPlatform),
      roleTracker = roleTracker,
      packages = irPackages
    )

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
