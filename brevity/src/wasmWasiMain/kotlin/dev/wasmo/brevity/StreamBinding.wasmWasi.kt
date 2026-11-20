@file:OptIn(UnsafeWasmMemoryApi::class, ExperimentalWasmInterop::class)

package dev.wasmo.brevity

import kotlin.wasm.unsafe.MemoryAllocator
import kotlin.wasm.unsafe.Pointer
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi

@BrevityInternalApi
abstract class StreamBinding<T> {
  abstract val byteCount: Int
  abstract fun new(): Long
  open fun read(index: Int, address: Int, count: Int): Int = error("streams only")
  open fun read(index: Int, address: Int): Int = error("futures only")
  open fun write(index: Int, address: Int, count: Int): Int = error("streams only")
  open fun write(index: Int, address: Int): Int = error("futures only")
  abstract fun cancelRead(index: Int): Int
  abstract fun cancelWrite(index: Int): Int
  abstract fun dropRead(index: Int)
  abstract fun dropWrite(index: Int)
  abstract fun store(memoryAllocator: MemoryAllocator, address: Pointer, value: T)
  abstract fun load(address: Pointer): T
}
