package dev.wasmo.brevity.cli

import com.github.ajalt.clikt.completion.CompletionCandidates
import com.github.ajalt.clikt.core.BadParameterValue
import com.github.ajalt.clikt.parameters.options.NullableOption
import com.github.ajalt.clikt.parameters.options.RawOption
import com.github.ajalt.clikt.parameters.options.convert
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

fun RawOption.okioReadableDirectory(
  fileSystem: FileSystem,
): NullableOption<Path, Path> {
  return convert({ localization.pathMetavar() }, CompletionCandidates.Path) { string ->
    string.toPath()
      .also { path ->
        if (fileSystem.metadataOrNull(path)?.isDirectory != true) {
          throw BadParameterValue("not a readable directory: $path", option, name)
        }
      }
  }
}

fun RawOption.okioWritableDirectory(
  fileSystem: FileSystem,
): NullableOption<Path, Path> {
  return convert({ localization.pathMetavar() }, CompletionCandidates.Path) { string ->
    string.toPath()
      .also { path ->
        if (fileSystem.metadataOrNull(path)?.isDirectory == false) {
          throw BadParameterValue("not a writable directory: $path", option, name)
        }
      }
  }
}
