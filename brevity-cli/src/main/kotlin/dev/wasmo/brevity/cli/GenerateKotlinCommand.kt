package dev.wasmo.brevity.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import dev.wasmo.brevity.collectNoIssuesOrThrow
import dev.wasmo.brevity.filterNamedWorlds
import dev.wasmo.brevity.kotlin.generator.WitBridgeGenerator
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

  override fun run() = collectNoIssuesOrThrow {
    val commonMainDir = outputKotlinCommonMain.toFile()
    commonMainDir.deleteRecursively()
    commonMainDir.mkdirs()

    val wasmWasiMainDir = outputKotlinWasmWasiMain.toFile()
    wasmWasiMainDir.deleteRecursively()
    wasmWasiMainDir.mkdirs()

    val jvmMainDir = outputKotlinJvmMain.toFile()
    jvmMainDir.deleteRecursively()
    jvmMainDir.mkdirs()

    val generator = WitBridgeGenerator.precompile(
      fileSystem = fileSystem,
      packageDirectories = inputWitDirectories,
      irFilter = {
        when {
          world.isEmpty() -> it
          else -> it.filterNamedWorlds(world)
        }
      },
    ) ?: return@collectNoIssuesOrThrow

    val projectSpec = generator.generate()
    projectSpec.writeTo(
      fileSystem = fileSystem,
      commonMain = outputKotlinCommonMain,
      wasmWasiMain = outputKotlinWasmWasiMain,
      jvmMain = outputKotlinJvmMain,
    )
  }
}
