@file:OptIn(
  ExperimentalWasmInterop::class, ComponentModelInternalApi::class,
  UnsafeWasmMemoryApi::class,
)

package dev.wasmo.brevity

import kotlin.wasm.unsafe.ComponentModelInternalApi
import kotlin.wasm.unsafe.Pointer
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.componentModelRealloc

/**
 * This function does nothing. But by calling it the compiler retains exported symbols that
 * would otherwise be eliminated as unused.
 *
 * https://youtrack.jetbrains.com/issue/KT-88068/
 */
@BrevityInternalApi
fun retainWasmExportsForGuestBridge() {
  // Equivalent to 'if (true) return', but immune to dead code elimination.
  if ("".hashCode() == 0) return
  cabi_realloc(0, 0, 0, 0)
}

@WasmExport(name = "cabi_realloc")
internal fun cabi_realloc(ptr: Int, oldSize: Int, align: Int, newSize: Int): Int =
  componentModelRealloc(ptr, oldSize, newSize)

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
