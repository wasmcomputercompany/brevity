package dev.wasmo.brevity.kotlin.encoders

import dev.wasmo.brevity.kotlin.generator.Symbols

/**
 * Stores a string as two pointers: an address and a byte count.
 */
object StringEncoder : Encoder() {
  override val coreTypes: List<CoreType>
    get() = listOf(CoreType.Pointer)

  override fun EncodeBuilder.coreTypeToValue() {
    val pointer = nameAllocator.newName("pointer")
    val stringAddress = nameAllocator.newName("stringAddress")
    val stringByteCount = nameAllocator.newName("stringByteCount")

    code.addStatement("val %N = %T(%L.toUInt())", pointer, Symbols.KotlinWasm.Pointer, take())
    code.addStatement("val %N = %N.%M()", stringAddress, pointer, Symbols.Brevity.LoadPointer)
    code.addStatement("val %N = (%N + 4).loadInt()", stringByteCount, pointer)
    put("%N.%M(%N)", stringAddress, Symbols.Brevity.LoadString, stringByteCount)
  }

  override fun EncodeBuilder.valueToCoreType() {
    val byteArray = nameAllocator.newName("byteArray")
    val pointer = nameAllocator.newName("pointer")

    code.addStatement("val %N = %L.%M()", byteArray, take(), Symbols.Kotlin.EncodeToByteArray)
    code.addStatement("val %N = %L", pointer, allocate("%N.size", byteArray))
    code.addStatement("%N.%M(%N)", pointer, Symbols.Brevity.StoreByteArray, byteArray)
    put("%N.address.toInt()", pointer)
//    put("%N.size", byteArray) // TODO: support 2x put on strings
  }
}
