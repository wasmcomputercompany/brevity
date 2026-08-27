@file:OptIn(ExperimentalWasmInterop::class, ComponentModelInternalApi::class)

package dev.wasmo.brevity

import kotlin.wasm.unsafe.ComponentModelInternalApi
import kotlin.wasm.unsafe.componentModelRealloc

/**
 * This function does nothing. But by calling it the compiler retains exported symbols that
 * would otherwise be eliminated as unused.
 *
 * https://youtrack.jetbrains.com/issue/KT-88068/
 */
fun retainWasmExportsForGuestBridge() {
  // Equivalent to 'if (true) return', but immune to dead code elimination.
  if ("".hashCode() == 0) return
  cabi_realloc(0, 0, 0, 0)
}

@WasmExport(name = "cabi_realloc")
internal fun cabi_realloc(ptr: Int, oldSize: Int, align: Int, newSize: Int): Int =
  componentModelRealloc(ptr, oldSize, newSize)
