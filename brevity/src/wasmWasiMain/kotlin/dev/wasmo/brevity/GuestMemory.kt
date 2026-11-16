@file:OptIn(
  ExperimentalWasmInterop::class, ComponentModelInternalApi::class,
  UnsafeWasmMemoryApi::class,
)

package dev.wasmo.brevity

import kotlin.wasm.unsafe.ComponentModelInternalApi
import kotlin.wasm.unsafe.Pointer
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi

@BrevityInternalApi
fun Pointer.loadPointer(): Pointer {
  return Pointer(loadInt().toUInt())
}

@BrevityInternalApi
fun Pointer.loadString(byteCount: Int): String {
  return loadByteArray(byteCount).decodeToString()
}

@BrevityInternalApi
fun Pointer.loadByteArray(byteCount: Int): ByteArray {
  val result = ByteArray(byteCount)
  for (i in 0 until byteCount) {
    result[i] = (this + i).loadByte()
  }
  return result
}

@BrevityInternalApi
fun Pointer.storeString(value: String) {
  val byteArray = value.encodeToByteArray()
  storeByteArray(byteArray)
}

@BrevityInternalApi
fun Pointer.storeByteArray(value: ByteArray) {
  for ((i, element) in value.withIndex()) {
    (this + i).storeByte(element)
  }
}
