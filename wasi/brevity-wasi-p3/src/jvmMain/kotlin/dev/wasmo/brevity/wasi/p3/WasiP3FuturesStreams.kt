@file:OptIn(BrevityInternalApi::class)

package dev.wasmo.brevity.wasi.p3

import com.dylibso.chicory.runtime.HostFunction
import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.runtime.Store
import com.dylibso.chicory.runtime.WasmFunctionHandle
import com.dylibso.chicory.wasm.types.FunctionType
import com.dylibso.chicory.wasm.types.ValType
import dev.wasmo.brevity.BrevityInternalApi
import dev.wasmo.brevity.HostBridge
import dev.wasmo.brevity.World

val wasiP3FuturesStreamsWorld: World<*, *>
  get() = RealWasiP3FuturesStreamsWorld()

/**
 * Shim until we implement streams and futures.
 */
@BrevityInternalApi
internal class RealWasiP3FuturesStreamsWorld : World<Unit, Unit>() {
  override val guest: Unit
    get() = Unit
  override val host: Unit
    get() = Unit

  override fun initExports(instance: Instance) {
  }

  override fun initImports(bridge: HostBridge, store: Store) {
    store.addFunction(
      HostFunction("wasi:cli/stdin@0.3.0",        "[stream-new-0]read-via-stream",                                   FunctionType.of(listOf(),                                      listOf(ValType.I64)), WasmFunctionHandle { instance, args -> TODO() }),
      HostFunction("wasi:cli/stdin@0.3.0",        "[stream-drop-readable-0]read-via-stream",                         FunctionType.of(listOf(ValType.I32),                           listOf<ValType>()),   WasmFunctionHandle { instance, args -> TODO() }),
      HostFunction("wasi:cli/stdin@0.3.0",        "[async-lower][future-read-1]read-via-stream",                     FunctionType.of(listOf(ValType.I32,ValType.I32),               listOf(ValType.I32)), WasmFunctionHandle { instance, args -> TODO() }),
      HostFunction("wasi:cli/stdin@0.3.0",        "[future-drop-readable-1]read-via-stream",                         FunctionType.of(listOf(ValType.I32),                           listOf<ValType>()),   WasmFunctionHandle { instance, args -> TODO() }),
      HostFunction("wasi:cli/stdout@0.3.0",       "[async-lower][future-read-1]write-via-stream",                    FunctionType.of(listOf(ValType.I32, ValType.I32),              listOf(ValType.I32)), WasmFunctionHandle { instance, args -> TODO() }),
      HostFunction("wasi:cli/stdout@0.3.0",       "[future-drop-readable-1]write-via-stream",                        FunctionType.of(listOf(ValType.I32),                           listOf<ValType>()),   WasmFunctionHandle { instance, args -> TODO() }),
      HostFunction("wasi:filesystem/types@0.3.0", "[async-lower][stream-write-0][method]descriptor.read-via-stream", FunctionType.of(listOf(ValType.I32, ValType.I32, ValType.I32), listOf(ValType.I32)), WasmFunctionHandle { instance, args -> TODO() }),
      HostFunction("wasi:filesystem/types@0.3.0", "[stream-cancel-read-0][method]descriptor.read-via-stream",        FunctionType.of(listOf(ValType.I32),                           listOf(ValType.I32)), WasmFunctionHandle { instance, args -> TODO() }),
      HostFunction("wasi:filesystem/types@0.3.0", "[stream-cancel-write-0][method]descriptor.read-via-stream",       FunctionType.of(listOf(ValType.I32),                           listOf(ValType.I32)), WasmFunctionHandle { instance, args -> TODO() }),
      HostFunction("wasi:filesystem/types@0.3.0", "[stream-drop-readable-0][method]descriptor.read-via-stream",      FunctionType.of(listOf(ValType.I32),                           listOf<ValType>()),   WasmFunctionHandle { instance, args -> TODO() }),
      HostFunction("wasi:filesystem/types@0.3.0", "[stream-drop-writable-0][method]descriptor.read-via-stream",      FunctionType.of(listOf(ValType.I32),                           listOf<ValType>()),   WasmFunctionHandle { instance, args -> TODO() }),
    )
  }
}
