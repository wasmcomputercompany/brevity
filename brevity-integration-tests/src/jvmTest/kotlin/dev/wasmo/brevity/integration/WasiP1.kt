package dev.wasmo.brevity.integration

import okio.Buffer

val STDOUT = 1
val STDERR = 2

interface WasiP1 {
  fun write(fd: Int, buffer: Buffer): Errno
}
