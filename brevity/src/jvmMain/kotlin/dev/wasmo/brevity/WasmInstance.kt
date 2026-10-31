@file:OptIn(BrevityInternalApi::class)

package dev.wasmo.brevity

import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.runtime.Store
import com.dylibso.chicory.runtime.internal.GcRefStore
import com.dylibso.chicory.wasm.Parser
import com.dylibso.chicory.wasm.WasmModule
import java.lang.reflect.Field
import kotlinx.coroutines.CoroutineScope
import okio.FileSystem
import okio.Path

class WasmInstance private constructor(
  val store: Store,
  val wasmModule: WasmModule,
  val instance: Instance,
) {
  private val gcRefs: GcRefStore = run {
    val field = Instance::class.java.getDeclaredField("gcRefs")
    field.isAccessible = true
    field.get(instance) as GcRefStore
  }
  private val sweepRequested: Field = GcRefStore::class.java.getDeclaredField("sweepRequested")
    .apply {
      isAccessible = true
    }

  /**
   * There's a bug in Chicory where re-entrant functions are considered to have an empty stack, and
   * that causes the GC to run when it shouldn't. We currently hack around this by manually running
   * the GC. This isn't a good long-term strategy.
   */
  fun gc() {
    sweepRequested.set(gcRefs, true)
    gcRefs.safePoint()
  }

  companion object {
    operator fun invoke(
      scope: CoroutineScope,
      fileSystem: FileSystem = FileSystem.SYSTEM,
      path: Path,
      name: String = "name",
      worlds: List<World<*, *>>,
    ): WasmInstance {
      val wasmModule = fileSystem.read(path) {
        Parser.parse(readByteArray())
      }
      return invoke(scope, wasmModule, name, worlds)
    }

    operator fun invoke(
      scope: CoroutineScope,
      wasmModule: WasmModule,
      name: String = "name",
      worlds: List<World<*, *>>,
    ): WasmInstance {
      val store = Store()
      val symbolIndex = SymbolIndex(wasmModule)

      val bridge = HostBridge(coroutineScope = scope)
      for (world in worlds) {
        world.initImports(bridge, store)
      }

      val instance = store.instantiate(name, wasmModule)
      bridge.init(symbolIndex, instance)
      for (world in worlds) {
        world.initExports(instance)
      }

      return WasmInstance(
        store = store,
        wasmModule = wasmModule,
        instance = instance,
      )
    }
  }
}

/**
 * Index into a [WasmModule] of the symbols Brevity cares about.
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
