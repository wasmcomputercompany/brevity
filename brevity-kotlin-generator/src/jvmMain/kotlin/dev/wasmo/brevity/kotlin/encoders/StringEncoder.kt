package dev.wasmo.brevity.kotlin.encoders

import dev.wasmo.brevity.Identifier

/**
 * Stores a string as two pointers: an address and a byte count.
 */
object StringEncoder : Encoder() {
  override val coreTypes: List<CoreType>
    get() = listOf(CoreType.Pointer, CoreType.Pointer)

  override val nameHints: List<Identifier>
    get() = listOf(Identifier("pointer"), Identifier("byte-count"))

  override fun EncodeBuilder.coreTypeToValue() {
    put(platform.loadString(take(), take()))
  }

  override fun EncodeBuilder.valueToCoreType() {
    val (address, size) = platform.storeString(take())
    put(address)
    put(size)
  }
}
