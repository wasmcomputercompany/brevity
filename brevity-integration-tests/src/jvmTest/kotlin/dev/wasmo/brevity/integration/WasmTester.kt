package dev.wasmo.brevity.integration

import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.runtime.Store
import com.dylibso.chicory.wabt.Wat2Wasm
import com.dylibso.chicory.wasm.Parser
import com.dylibso.chicory.wasm.WasmModule
import dev.wasmo.brevity.World
import okio.FileSystem
import okio.Path

class WasmTester(
  val store: Store,
  val wasmModule: WasmModule,
  val instance: Instance,
) {
  class Builder {
    private val worlds = mutableListOf<World<*, *>>()
    private var wasmBytes: ByteArray? = null

    fun wasmPath(path: Path) = apply {
      wasmBytes = FileSystem.SYSTEM.read(path) {
        readByteArray()
      }
    }

    fun wat(wat: String) = apply {
      wasmBytes = Wat2Wasm.parse(wat)
    }

    fun addWorld(world: World<*, *>) = apply {
      worlds += world
    }

    fun build(): WasmTester {
      check(wasmBytes != null) { "call wasmPath() or wat() first" }
      val wasmModule = Parser.parse(wasmBytes)
      val store = Store()
      for (world in worlds) {
        world.initImports(store)
      }

      val instance = store.instantiate("name", wasmModule)
      for (world in worlds) {
        world.initExports(instance)
      }

      return WasmTester(
        store = store,
        wasmModule = wasmModule,
        instance = instance,
      )
    }
  }
}

