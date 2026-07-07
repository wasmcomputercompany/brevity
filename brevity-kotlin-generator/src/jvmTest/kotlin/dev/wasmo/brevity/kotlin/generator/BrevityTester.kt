package dev.wasmo.brevity.kotlin.generator

import dev.wasmo.brevity.DeclarationIndex
import dev.wasmo.brevity.WorldIndex
import dev.wasmo.brevity.io.IoWitPackageReader
import dev.wasmo.brevity.ir.IrMapper
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

class BrevityTester(
  vararg files: Pair<Path, String>,
) {
  val packageDirectories = mutableSetOf<Path>()

  val packageReader = IoWitPackageReader(
    FakeFileSystem()
      .apply {
        for ((path, content) in files) {
          packageDirectories += path.parent!!
          createDirectories(path.parent!!)
          write(path) {
            writeUtf8(content)
          }
        }
      },
  )

  val witPackages = packageDirectories.map { directory ->
    packageReader.read(directory)
  }

  val irPackages = IrMapper(witPackages).map()
  val declarationIndex = DeclarationIndex(irPackages)
  val worldIndex = WorldIndex(declarationIndex, irPackages)

  val apiFiles = buildMap {
    val apiGenerator = ApiGenerator(irPackages)
    for (fileSpec in apiGenerator.generate()) {
      put(fileSpec.relativePath.toPath(), fileSpec.toString())
    }
  }

  val guestFiles = buildMap {
    val guestGenerator = GuestGenerator(declarationIndex, worldIndex, irPackages)
    for (fileSpec in guestGenerator.generate()) {
      put(fileSpec.relativePath.toPath(), fileSpec.toString())
    }
  }

  val hostFiles = buildMap {
    val hostGenerator = HostGenerator(declarationIndex, worldIndex, irPackages)
    for (fileSpec in hostGenerator.generate()) {
      put(fileSpec.relativePath.toPath(), fileSpec.toString())
    }
  }

  val files = buildMap {
    putAll(apiFiles)
    putAll(guestFiles)
    putAll(hostFiles)
  }

  operator fun get(path: Path): String? = files[path]
}
