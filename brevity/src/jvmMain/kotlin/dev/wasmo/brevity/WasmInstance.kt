package dev.wasmo.brevity

import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.runtime.Store
import com.dylibso.chicory.runtime.internal.GcRefStore
import com.dylibso.chicory.wasm.Parser
import com.dylibso.chicory.wasm.WasmModule
import java.lang.reflect.Field
import okio.FileSystem
import okio.Path

class WasmInstance(
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
      fileSystem: FileSystem = FileSystem.SYSTEM,
      path: Path,
      name: String = "name",
      worlds: List<World<*, *>>,
    ): WasmInstance {
      val wasmModule = fileSystem.read(path) {
        Parser.parse(readByteArray())
      }
      return invoke(wasmModule, name, worlds)
    }

    operator fun invoke(
      wasmModule: WasmModule,
      name: String = "name",
      worlds: List<World<*, *>>,
    ): WasmInstance {
      val store = Store()
      for (world in worlds) {
        world.initImports(store)
      }

      val instance = store.instantiate(name, wasmModule)
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

