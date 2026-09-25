package dev.wasmo.brevity

import com.dylibso.chicory.runtime.HostFunction
import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.runtime.Store
import com.dylibso.chicory.runtime.WasmFunctionHandle
import com.dylibso.chicory.wasm.types.FunctionType
import com.dylibso.chicory.wasm.types.ValType

@BrevityInternalApi
fun Async.World(
  hostFactory: (Async.Guest) -> Async.Host,
): World<Async.Host, Async.Guest> {
  val bridge = HostBridge() // TODO: share this among all worlds
  val guest = BridgeAsync.BridgeGuest()
  val host = hostFactory(guest)
  return BridgeAsync(bridge, guest, host)
}

@BrevityInternalApi
internal class BridgeAsync(
  private val bridge: HostBridge,
  override val guest: Async.Guest,
  override val host: Async.Host,
) : World<Async.Host, Async.Guest> {

  override fun initExports(instance: Instance) {
  }

  override fun initImports(store: Store) {
    store.addFunction(
      HostFunction(
        $$"$root",
        "[waitable-join]",
        FunctionType.of(
          listOf(ValType.I32, ValType.I32),
          listOf(),
        ),
        WasmFunctionHandle { instance, args ->
          bridge.get<WaitableSet>(args[1].toInt()).join(args[0].toInt())
          longArrayOf()
        },
      ),
    )
    store.addFunction(
      HostFunction(
        $$"$root",
        "[waitable-set-poll]",
        FunctionType.of(
          listOf(ValType.I32, ValType.I32),
          listOf(ValType.I32),
        ),
        WasmFunctionHandle { instance, args ->
          val result = bridge.get<WaitableSet>(args[0].toInt()).poll(args[1].toInt())
          longArrayOf(result.toLong())
        },
      ),
    )
    addContextFunctions(store, 0)
    addContextFunctions(store, 1)
    store.addFunction(
      HostFunction(
        $$"$root",
        "[waitable-set-new]",
        FunctionType.of(
          listOf(),
          listOf(ValType.I32),
        ),
        WasmFunctionHandle { instance, args ->
          val waitableSet = host.waitableSetNew()
          val result = bridge.toId(waitableSet)
          longArrayOf(result.toLong())
        },
      ),
    )
    store.addFunction(
      HostFunction(
        $$"$root",
        "[waitable-set-drop]",
        FunctionType.of(
          listOf(ValType.I32),
          listOf(),
        ),
        WasmFunctionHandle { instance, args ->
          val result = bridge.drop<WaitableSet>(args[0].toInt())
          result.close()
          longArrayOf()
        },
      ),
    )
    store.addFunction(
      HostFunction(
        $$"[export]$root",
        "[task-cancel]",
        FunctionType.of(listOf(), listOf()),
        WasmFunctionHandle { instance, args ->
          host.taskCancel()
          longArrayOf()
        },
      ),
    )
  }

  private fun addContextFunctions(store: Store, index: Int) {
    store.addFunction(
      HostFunction(
        $$"$root",
        "[context-get-$index]",
        FunctionType.of(
          listOf(),
          listOf(ValType.I32),
        ),
        WasmFunctionHandle { instance, args ->
          val result = host.contextGet(index, TypeId.I32)
          longArrayOf(result.toLong())
        },
      ),
    )
    store.addFunction(
      HostFunction(
        $$"$root",
        "[context-set-$index]",
        FunctionType.of(
          listOf(ValType.I32),
          listOf(),
        ),
        WasmFunctionHandle { instance, args ->
          host.contextSet(index, TypeId.I32, args[0].toInt())
          longArrayOf()
        },
      ),
    )
  }

  class BridgeGuest : Async.Guest
}
