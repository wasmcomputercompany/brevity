@file:OptIn(
  UnsafeWasmMemoryApi::class,
  ExperimentalWasmInterop::class,
  ComponentModelInternalApi::class,
)

package dev.wasmo.brevity

import kotlin.wasm.unsafe.ComponentModelInternalApi
import kotlin.wasm.unsafe.Pointer
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi

object GuestBridge {
  private val idToResource = mutableMapOf<Int, Resource>()
  private var nextId = 4_040_000

  fun <T : Resource> toId(resource: T): Int {
    val id = nextId++
    idToResource[id] = resource
    return id
  }

  fun <T : Resource> fromId(id: Int, constructor: (Int) -> T): T {
    return constructor(id)
  }
}

fun Pointer.loadPointer(): Pointer {
  return Pointer(loadInt().toUInt())
}

fun Pointer.loadString(byteCount: Int): String {
  return loadByteArray(byteCount).decodeToString()
}

fun Pointer.loadByteArray(byteCount: Int): ByteArray {
  val result = ByteArray(byteCount)
  for (i in 0 until byteCount) {
    result[i] = (this + i).loadByte()
  }
  return result
}

fun Pointer.storeString(value: String) {
  val byteArray = value.encodeToByteArray()
  storeByteArray(byteArray)
}

fun Pointer.storeByteArray(value: ByteArray) {
  for ((i, element) in value.withIndex()) {
    (this + i).storeByte(element)
  }
}
