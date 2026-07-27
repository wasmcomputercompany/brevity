package dev.wasmo.brevity.integration

import okio.Buffer

class FakeWasiP1 : WasiP1 {
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
