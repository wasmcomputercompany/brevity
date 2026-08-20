package dev.wasmo.brevity

import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.runtime.Store
import com.dylibso.chicory.wasm.Parser
import com.dylibso.chicory.wasm.WasmModule
import okio.FileSystem
import okio.Path

class WasmInstance(
  val store: Store,
  val wasmModule: WasmModule,
  val instance: Instance,
) {
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

