package dev.wasmo.brevity.kotlin.encoders

import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.kotlin.generator.Symbols

/**
 * Stores a string as two pointers: an address and a byte count.
 */
object StringEncoder : Encoder() {
  override val coreTypes: List<CoreType>
    get() = listOf(CoreType.Pointer, CoreType.Pointer)

  override val nameHints: List<Identifier>
    get() = listOf(Identifier("pointer"), Identifier("byte-count"))

  override fun EncodeBuilder.coreTypeToValue() {
    put(
      "%T(%L.toUInt()).%M(%L)",
      Symbols.KotlinWasm.Pointer,
      take(),
      Symbols.Brevity.LoadString,
      take(),
    )
  }

  override fun EncodeBuilder.valueToCoreType() {
    val byteArray = nameAllocator.newName("byteArray")
    val pointer = nameAllocator.newName("pointer")

    code.addStatement("val %N = %L.%M()", byteArray, take(), Symbols.Kotlin.EncodeToByteArray)
    code.addStatement("val %N = %L", pointer, allocate("%N.size", byteArray))
    code.addStatement("%N.%M(%N)", pointer, Symbols.Brevity.StoreByteArray, byteArray)
    put("%N.address.toInt()", pointer)
    put("%N.size", byteArray)
  }
}
