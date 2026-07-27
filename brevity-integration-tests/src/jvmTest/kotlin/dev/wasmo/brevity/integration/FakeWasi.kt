package dev.wasmo.brevity.integration

import okio.Buffer
import wit.wasi.v0_1.Errno
import wit.wasi.v0_1.STDERR
import wit.wasi.v0_1.STDOUT
import wit.wasi.v0_1.Wasi

class FakeWasi : Wasi.Host {
  val stderr = Buffer()
  val stdout = Buffer()

  override fun write(fd: Int, buffer: Buffer): Errno {
    when (fd) {
      STDOUT -> {
        stdout.write(buffer, buffer.size)
        return Errno.success
      }
      STDERR -> {
        stderr.write(buffer, buffer.size)
        return Errno.success
      }
      else -> return Errno.badf
    }
  }
}
