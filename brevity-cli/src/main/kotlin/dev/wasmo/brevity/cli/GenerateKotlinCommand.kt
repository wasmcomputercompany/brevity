package dev.wasmo.brevity.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import dev.wasmo.brevity.io.IoWitPackageReader
import dev.wasmo.brevity.ir.IrMapper
import dev.wasmo.brevity.kotlin.generator.ApiGenerator
import dev.wasmo.brevity.kotlin.generator.GuestGenerator
import dev.wasmo.brevity.kotlin.generator.HostGenerator
import dev.wasmo.brevity.kotlin.generator.KtMapper
import java.nio.file.Path
import okio.FileSystem
import okio.Path.Companion.toOkioPath

class GenerateKotlinCommand : CliktCommand(
  name = "generate-kotlin",
) {
  val inputWitDirectories: List<Path> by option("--wit")
    .path(mustExist = true, canBeFile = false, canBeDir = true)
    .multiple(required = true)
    .help("each directory should contain a single package")
  val outputKotlinCommonMain: Path by option("--commonMain")
    .path(canBeFile = false, canBeDir = true)
    .required()
  val outputKotlinWasmWasiMain: Path by option("--wasmWasiMain")
    .path(canBeFile = false, canBeDir = true)
    .required()
  val outputKotlinJvmMain: Path by option("--jvmMain")
    .path(canBeFile = false, canBeDir = true)
    .required()

  override fun run() {
    val packageReader = IoWitPackageReader(FileSystem.SYSTEM)
    val ktMapper = KtMapper(onlyLongs = true)
    val apiGenerator = ApiGenerator()
    val guestGenerator = GuestGenerator()
    val hostGenerator = HostGenerator()

    val ioWitPackages = inputWitDirectories.map {
      packageReader.read(it.toOkioPath())
    }

    val commonMainDir = outputKotlinCommonMain.toFile()
    commonMainDir.mkdirs()

    val wasmWasiMainDir = outputKotlinWasmWasiMain.toFile()
    wasmWasiMainDir.mkdirs()

    val jvmMainDir = outputKotlinJvmMain.toFile()
    jvmMainDir.mkdirs()

    val irPackages = IrMapper(ioWitPackages).map()
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
