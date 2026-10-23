package dev.wasmo.brevity.integration

import java.util.concurrent.ConcurrentHashMap
import okio.Buffer
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.Path
import okio.Sink

/**
 * Skips writing when the bytes match the existing file to avoid rebuilding unchanged files for
 * timestamp-driven build tools. Keeps track of files written so stale files can be swept.
 */
class WriteOnlyIfChangedFileSystem(delegate: FileSystem) : ForwardingFileSystem(delegate) {
  private val written: MutableSet<Path> = ConcurrentHashMap.newKeySet()

  fun wasFileWritten(file: Path): Boolean {
    return file in written
  }

  override fun sink(file: Path, mustCreate: Boolean): Sink {
    written += file
    val buffer = Buffer()
    return object : Sink by buffer {
      override fun close() {
        val content = buffer.readByteString()
        val existing = if (delegate.exists(file)) delegate.read(file) { readByteString() } else null
        if (content != existing) delegate.write(file, mustCreate) { write(content) }
      }
    }
  }
}
