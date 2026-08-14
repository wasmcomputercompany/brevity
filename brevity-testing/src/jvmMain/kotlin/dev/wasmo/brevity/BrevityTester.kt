package dev.wasmo.brevity

import dev.wasmo.brevity.kotlin.generator.WitBridgeGenerator
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

class BrevityTester(
  val generator: WitBridgeGenerator,
) {
  val roleTracker = generator.roleTracker

  private val apiFiles = buildMap {
    for (fileSpec in generator.api.generate()) {
      put(fileSpec.relativePath.toPath(), fileSpec.toString())
    }
  }.toSortedMap()

  private val guestFiles = buildMap {
    for (fileSpec in generator.guest.generate()) {
      put(fileSpec.relativePath.toPath(), fileSpec.toString())
    }
  }.toSortedMap()

  private val hostFiles = buildMap {
    for (fileSpec in generator.host.generate()) {
      put(fileSpec.relativePath.toPath(), fileSpec.toString())
    }
  }.toSortedMap()

  val files = buildMap {
    putAll(apiFiles)
    putAll(guestFiles)
    putAll(hostFiles)
  }.toSortedMap()

  operator fun get(path: Path): String? = files[path]

  companion object {
    operator fun invoke(vararg files: Pair<Path, String>): BrevityTester {
       val packageDirectories = mutableSetOf<Path>()

       val fileSystem = FakeFileSystem()
        .apply {
          for ((path, content) in files) {
            packageDirectories += path.parent!!
            createDirectories(path.parent!!)
            write(path) {
              writeUtf8(content)
            }
          }
        }
      val generator = withIssueCollector {
        WitBridgeGenerator.precompile(fileSystem, packageDirectories)
      }!!

      return BrevityTester(generator)
    }
  }
}
