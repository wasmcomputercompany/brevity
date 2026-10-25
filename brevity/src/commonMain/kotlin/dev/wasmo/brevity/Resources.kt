package dev.wasmo.brevity

abstract class Resource : AutoCloseable {
  @BrevityInternalApi
  var hasRep = false

  @BrevityInternalApi
  var rep = Int.MIN_VALUE

  override fun close() {
  }
}
