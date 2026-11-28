@file:OptIn(UnsafeWasmMemoryApi::class, ExperimentalWasmInterop::class)

package dev.wasmo.brevity

import kotlin.wasm.unsafe.Pointer
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi

/** Load a list of values from linear memory. */
@BrevityInternalApi
internal interface Loader<T> {
  fun load(
    binding: StreamBinding<T>,
    address: Pointer,
    count: Int,
  )

  class ListLoader<T> : Loader<T> {
    var list: List<T> = listOf()
      private set

    override fun load(
      binding: StreamBinding<T>,
      address: Pointer,
      count: Int,
    ) {
      val byteCount = binding.byteCount
      list = List(count) { index ->
        binding.load(address + (byteCount * index))
      }
    }
  }

  object UnitLoader : Loader<Unit> {
    override fun load(
      binding: StreamBinding<Unit>,
      address: Pointer,
      count: Int,
    ) {
    }
  }
}
