package dev.wasmo.brevity.gradle

import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

@CacheableTask
internal abstract class DownloadOciWitTask : DefaultTask() {
  init {
    group = "brevity"
    description = "download WIT files from OCI registry"
  }

  @get:OutputDirectory
  internal abstract val witOutputDir: DirectoryProperty

  @get:Inject
  abstract val execOperations: ExecOperations

  @get:Input
  abstract val ociPackages: ListProperty<String>

  @TaskAction
  fun execute() {
    witOutputDir.get().asFile.mkdirs()

    // We download our WASI WIT dependencies directly here. Each package is
    // downloaded to a corresponding folder, with `:` replaced with `_`.
    // Each folder contains a single file named "package.wit".
    //
    // wkg can automatically fetch all a package's dependencies.
    // That's definitely the way to go in a situation where we're dealing
    // with real wit files, but here where we need a set of wit files for
    // a named list of packages, it's more straightforward and faster
    // to download them directly.
    for (packageName in ociPackages.get()) {
      val outputFileName = packageName.replace(":", "_") +
        "/package.wit"
      val outputPath = File(witOutputDir.get().asFile, outputFileName)

      if (!outputPath.exists()) {
        outputPath.parentFile.mkdirs()
        execOperations.exec {
          commandLine(
            Paths.probe("wkg"),
            "get",
            "--output", outputPath.absolutePath,
            packageName,
          )
        }
      }
    }
  }
}
