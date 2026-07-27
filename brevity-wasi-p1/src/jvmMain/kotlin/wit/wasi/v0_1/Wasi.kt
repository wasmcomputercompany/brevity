// Copyright 2019-2023 the Contributors to the WASI Specification
package wit.wasi.v0_1

import okio.Buffer

/**
 * This is manually-authored interface for WASI Preview 1, as required by Kotlin/Wasm.
 *
 * We can't use Brevity or Wit to generate this, because the WASI p1 API is not expressable in .wit.
 *
 * https://github.com/WebAssembly/WASI/blob/wasi-0.1/preview1/docs.md
 */
object Wasi {
  interface Guest

  interface Host {
    fun write(fd: Int, buffer: Buffer): Errno
  }
}
