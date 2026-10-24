package dev.wasmo.brevity

abstract class Resource : AutoCloseable {
  @BrevityInternalApi
  var hasIndex = false

  @BrevityInternalApi
  var index = Int.MIN_VALUE

  override fun close() {
  }
}
