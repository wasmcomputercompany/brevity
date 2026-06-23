package dev.wasmo.brevity

interface Resource : AutoCloseable {
  override fun close() {
  }
}
