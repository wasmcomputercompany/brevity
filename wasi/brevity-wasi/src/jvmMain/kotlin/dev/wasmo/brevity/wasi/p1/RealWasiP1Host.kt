package dev.wasmo.brevity.wasi.p1

import okio.Buffer
import wit.wasi.v0_1.Errno
import wit.wasi.v0_1.STDERR
import wit.wasi.v0_1.STDOUT
import wit.wasi.v0_1.Wasi

class RealWasiP1Host : Wasi.Host {
  override fun write(fd: Int, buffer: Buffer): Errno {
    val out = when (fd) {
      STDOUT -> System.out
      STDERR -> System.err
      else -> return Errno.badf
    }
    out.write(buffer.readByteArray())
    return Errno.success
  }
}
