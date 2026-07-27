@file:OptIn(
  ComponentModelInternalApi::class,
  ExperimentalWasmInterop::class,
  UnsafeWasmMemoryApi::class,
)

package dev.wasmo.brevity.integration

import kotlin.wasm.unsafe.ComponentModelInternalApi
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.componentModelRealloc

@WasmExport(name = "cabi_realloc")
fun cabi_realloc(ptr: Int, oldSize: Int, align: Int, newSize: Int): Int =
  componentModelRealloc(ptr, oldSize, newSize)
