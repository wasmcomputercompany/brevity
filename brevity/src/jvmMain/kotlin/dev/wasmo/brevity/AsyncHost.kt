@file:OptIn(BrevityInternalApi::class)

package dev.wasmo.brevity

import com.dylibso.chicory.runtime.HostFunction
import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.runtime.Store
import com.dylibso.chicory.runtime.WasmFunctionHandle
import com.dylibso.chicory.wasm.types.FunctionType
import com.dylibso.chicory.wasm.types.ValType

val asyncWorld: World<*, *>
  get() = RealAsyncWorld()

@BrevityInternalApi
internal class RealAsyncWorld : World<Async.Host, Async.Guest>() {
  private val host_ = RealAsyncHost()

  override val guest = BridgeGuest()
  override val host: Async.Host
    get() = host_

  private lateinit var bridge: HostBridge

  override fun initExports(instance: Instance) {
  }

  override fun initImports(bridge: HostBridge, store: Store) {
    this.bridge = bridge
    this.host_.bridge = bridge

    store.addFunction(
      HostFunction(
        $$"$root",
        "[waitable-join]",
        FunctionType.of(
          listOf(ValType.I32, ValType.I32),
          listOf(),
        ),
        WasmFunctionHandle { instance, args ->
          val waitableIndex = args[0].toInt()
          val rep = args[1].toInt()
          if (rep != 0) {
            bridge.getImportedResource<WaitableSet>(rep).join(waitableIndex)
          } else {
            // TODO: remove the waitable from its current waitable set.
          }
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
          val result = bridge.getImportedResource<WaitableSet>(args[0].toInt()).poll(args[1].toInt())
          longArrayOf(result.toLong())
        },
      ),
    )
    store.addFunction(
      HostFunction(
        $$"$root",
        "[waitable-set-wait]",
        FunctionType.of(
          listOf(ValType.I32, ValType.I32),
          listOf(ValType.I32),
        ),
        WasmFunctionHandle { instance, args ->
          val waitableSet = bridge.getImportedResource<WaitableSet>(args[0].toInt())
          val payloadAddress = args[1].toInt()
          val event = waitableSet.wait()
          bridge.memory.writeI32(payloadAddress, event.p1)
          bridge.memory.writeI32(payloadAddress + 4, event.p2)
          longArrayOf(event.code.toLong())
        },
      ),
    )

    // Context get + set functions are imported with two names each.
    addContextFunctions(store, 0, $$"$root", "[context-get-0]", "[context-set-0]")
    addContextFunctions(store, 0, "env", "__wasm_get_stack_pointer", "__wasm_set_stack_pointer")
    addContextFunctions(store, 1, $$"$root", "[context-get-1]", "[context-set-1]")
    addContextFunctions(store, 1, "env", "__wasm_get_tls_base", "__wasm_set_tls_base")

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
          waitableSet.rep = bridge.putImportedResource(waitableSet)
          val result = waitableSet.rep
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
          // TODO: implement this.
          // val result = bridge.drop<WaitableSet>(args[0].toInt())
          // result.close()
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
    store.addFunction(
      HostFunction(
        $$"$root",
        "[subtask-cancel]",
        FunctionType.of(
          listOf(ValType.I32),
          listOf(ValType.I32),
        ),
        WasmFunctionHandle { instance, args ->
          val subtaskStateOrNone = host.subtaskCancel(args[0].toInt())
          longArrayOf(subtaskStateOrNone.toLong())
        },
      ),
    )
    store.addFunction(
      HostFunction(
        $$"$root",
        "[subtask-drop]",
        FunctionType.of(
          listOf(ValType.I32),
          listOf(),
        ),
        WasmFunctionHandle { instance, args ->
          host.subtaskDrop(args[0].toInt())
          longArrayOf()
        },
      ),
    )
  }

  private fun addContextFunctions(
    store: Store,
    index: Int,
    moduleName: String,
    getSymbolName: String,
    setSymbolName: String,
  ) {
    store.addFunction(
      HostFunction(
        moduleName,
        getSymbolName,
        FunctionType.of(
          listOf(),
          listOf(ValType.I32),
        ),
        WasmFunctionHandle { instance, args ->
          val result = bridge.currentThread.contextGet(index)
          longArrayOf(result.toLong())
        },
      ),
    )
    store.addFunction(
      HostFunction(
        moduleName,
        setSymbolName,
        FunctionType.of(
          listOf(ValType.I32),
          listOf(),
        ),
        WasmFunctionHandle { instance, args ->
          bridge.currentThread.contextSet(index, args[0].toInt())
          longArrayOf()
        },
      ),
    )
  }

  class BridgeGuest : Async.Guest
}
