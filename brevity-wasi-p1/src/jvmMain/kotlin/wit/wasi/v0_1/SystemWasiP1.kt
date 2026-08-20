package wit.wasi.v0_1

import okio.Buffer

object SystemWasiP1 : Wasi.Host {
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
