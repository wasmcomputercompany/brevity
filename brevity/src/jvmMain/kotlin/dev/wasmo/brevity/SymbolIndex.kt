package dev.wasmo.brevity

import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.wasm.WasmModule

/**
 * Index into a [com.dylibso.chicory.wasm.WasmModule] of the symbols Brevity cares about.
 */
@BrevityInternalApi
class SymbolIndex(
  private val initialStackPointerGlobal: Int,
  private val initialTlsBaseGlobal: Int,
) {
  fun initialStackPointer(instance: Instance): Int {
    return when (initialStackPointerGlobal) {
      -1 -> 0
      else -> instance.global(initialStackPointerGlobal).value.toInt()
    }
  }

  fun initialTlsBase(instance: Instance): Int {
    return when (initialTlsBaseGlobal) {
      -1 -> 0
      else -> instance.global(initialTlsBaseGlobal).value.toInt()
    }
  }

  companion object {
    operator fun invoke(wasmModule: WasmModule): SymbolIndex {
      var initialStackPointerGlobal = -1
      var initialTlsBaseGlobal = -1
      val nameSection = wasmModule.nameSection()

      if (nameSection != null) {
        val globalSection = wasmModule.globalSection()
        for (i in 0 until globalSection.globalCount()) {
          when (nameSection.nameOfGlobal(i)) {
            "__init_stack_pointer" -> initialStackPointerGlobal = i
            "__init_tls_base" -> initialTlsBaseGlobal = i
            else -> continue
          }
          if (initialStackPointerGlobal != -1 && initialTlsBaseGlobal != -1) break
        }
      }

      return SymbolIndex(initialStackPointerGlobal, initialTlsBaseGlobal)
    }
  }
}
