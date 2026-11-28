@file:OptIn(UnsafeWasmMemoryApi::class, ExperimentalWasmInterop::class)

package dev.wasmo.brevity

import kotlin.wasm.unsafe.MemoryAllocator
import kotlin.wasm.unsafe.Pointer
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi

/** Store a list of values into linear memory. */
@BrevityInternalApi
internal interface Storer<T> {
  fun store(
    binding: StreamBinding<T>,
    memoryAllocator: MemoryAllocator,
    address: Pointer,
  )

  class ListStorer<T>(
    private val list: List<T>,
  ) : Storer<T> {
    override fun store(
      binding: StreamBinding<T>,
      memoryAllocator: MemoryAllocator,
      address: Pointer,
    ) {
      val byteCount = binding.byteCount
      for ((i, element) in list.withIndex()) {
        binding.store(memoryAllocator, address + i * byteCount, element)
      }
    }
  }

  object UnitStorer : Storer<Unit> {
    override fun store(
      binding: StreamBinding<Unit>,
      memoryAllocator: MemoryAllocator,
      address: Pointer,
    ) {
    }
  }
}
