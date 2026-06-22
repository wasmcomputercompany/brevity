package dev.wasmo.brevity.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import dev.wasmo.brevity.filterNamedWorlds
import dev.wasmo.brevity.io.IoWitPackageReader
import dev.wasmo.brevity.ir.IrMapper
import dev.wasmo.brevity.kotlin.generator.ApiGenerator
import dev.wasmo.brevity.kotlin.generator.GuestGenerator
import dev.wasmo.brevity.kotlin.generator.HostGenerator
import dev.wasmo.brevity.kotlin.generator.KtMapper
import okio.FileSystem
import okio.Path

class GenerateKotlinCommand(
  private val fileSystem: FileSystem,
) : CliktCommand(
  name = "generate-kotlin",
) {
  val inputWitDirectories: List<Path> by option("--wit")
    .okioReadableDirectory(fileSystem)
    .multiple(required = true)
    .help("each directory should contain a single package")
  val outputKotlinCommonMain: Path by option("--commonMain")
    .okioWritableDirectory(fileSystem)
    .required()
  val outputKotlinWasmWasiMain: Path by option("--wasmWasiMain")
    .okioWritableDirectory(fileSystem)
    .required()
  val outputKotlinJvmMain: Path by option("--jvmMain")
    .okioWritableDirectory(fileSystem)
    .required()
  val world: List<String> by option("--world")
    .multiple()
    .help("the world name like 'command', 'wasi:cli/command', or 'wasi:cli/command@0.3.0'")

  override fun run() {
    val packageReader = IoWitPackageReader(fileSystem)
    val ktMapper = KtMapper()
    val apiGenerator = ApiGenerator()
    val guestGenerator = GuestGenerator()
    val hostGenerator = HostGenerator()

    val ioWitPackages = inputWitDirectories.map {
      packageReader.read(it)
    }

    val commonMainDir = outputKotlinCommonMain.toFile()
    commonMainDir.mkdirs()

    val wasmWasiMainDir = outputKotlinWasmWasiMain.toFile()
    wasmWasiMainDir.mkdirs()

    val jvmMainDir = outputKotlinJvmMain.toFile()
    jvmMainDir.mkdirs()

    val allIrPackages = IrMapper(ioWitPackages).map()

    val irPackages = when {
      world.isEmpty() -> allIrPackages
      else -> allIrPackages.filterNamedWorlds(world)
    }

    for (irPackage in irPackages) {
      val ktPackage = ktMapper.map(irPackage)

      val apiFileSpec = apiGenerator.generate(ktPackage)
      apiFileSpec.writeTo(commonMainDir)

      val guestFileSpec = guestGenerator.generate(ktPackage)
      guestFileSpec.writeTo(wasmWasiMainDir)

      val hostFileSpec = hostGenerator.generate(ktPackage)
      hostFileSpec.writeTo(jvmMainDir)
    }
  }
}
