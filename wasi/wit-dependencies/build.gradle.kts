abstract class DownloadWitFiles : DefaultTask() {
  init {
    group = "brevity"
    description = "download WIT files from OCI registry"
  }

  @get:OutputDirectory
  internal abstract val witOutputDir: DirectoryProperty

  @get:Inject
  abstract val execOperations: ExecOperations

  // A set of wit package names, e.g. wasi:cli@5.0.3
  @get:Input
  abstract val packageNames: ListProperty<String>

  @TaskAction
  fun execute() {
    witOutputDir.get().asFile.mkdirs()

    for (packageName in packageNames.get()) {
      val outputFileName = packageName.replace(":", "_") + ".wit"
      val outputPath = File(witOutputDir.get().asFile, outputFileName)

      if (!outputPath.exists()) {
        execOperations.exec {
          commandLine(
            "wkg",
            "get",
            "--output", outputPath.absolutePath,
            packageName,
          )
        }
      }
    }
  }
}

tasks.register<DownloadWitFiles>("downloadWitFiles") {
  witOutputDir = layout.buildDirectory.dir("wit/deps")

  // We download our WASI WIT dependencies directly here. Each package is
  // downloaded to a corresponding filename, with `:` replaced with `_`.
  //
  // wkg can automatically fetch all a package's dependencies.
  // That's definitely the way to go in a situation where we're dealing
  // with real wit files, but here where we need a set of wit files for
  // a named list of packages, it's more straightforward and faster
  // to download them directly.
  packageNames.add("wasi:cli@0.2.0")
  packageNames.add("wasi:clocks@0.2.0")
  packageNames.add("wasi:filesystem@0.2.0")
  packageNames.add("wasi:http@0.2.0")
  packageNames.add("wasi:io@0.2.0")
  packageNames.add("wasi:random@0.2.0")
  packageNames.add("wasi:sockets@0.2.0")
  packageNames.add("wasi:cli@0.3.1")
  packageNames.add("wasi:clocks@0.3.1")
  packageNames.add("wasi:filesystem@0.3.1")
  packageNames.add("wasi:http@0.3.1")
  packageNames.add("wasi:random@0.3.1")
}
