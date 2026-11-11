@file:OptIn(BrevityInternalApi::class)

package dev.wasmo.brevity

import com.dylibso.chicory.runtime.HostFunction
import com.dylibso.chicory.runtime.Store
import com.dylibso.chicory.runtime.WasmFunctionHandle
import com.dylibso.chicory.wasm.types.FunctionType
import com.dylibso.chicory.wasm.types.ValType

/**
 * Standard APIs to host components.
 */
@BrevityInternalApi
internal fun HostBridge.initImports(store: Store) {
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
        val waitableSetIndex = args[1].toInt()

        val waitable = getImportedResource<Waitable>(waitableIndex)
        waitable.join(
          waitableSet = when {
            waitableSetIndex == 0 -> null
            else -> getImportedResource(waitableSetIndex)
          },
        )

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
        val waitableSet = getImportedResource<WaitableSet>(args[0].toInt())
        val payloadAddress = args[1].toInt()
        val event = waitableSet.poll()
        memory.writeI32(payloadAddress, event.p1)
        memory.writeI32(payloadAddress + 4, event.p2)
        longArrayOf(event.code.toLong())
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
        val waitableSet = getImportedResource<WaitableSet>(args[0].toInt())
        val payloadAddress = args[1].toInt()
        val event = waitableSet.wait()
        memory.writeI32(payloadAddress, event.p1)
        memory.writeI32(payloadAddress + 4, event.p2)
        longArrayOf(event.code.toLong())
      },
    ),
  )

  // Context get + set functions are imported with two names each.
  addStackPointerFunctions(store, $$"$root", "[context-get-0]", "[context-set-0]")
  addStackPointerFunctions(store, "env", "__wasm_get_stack_pointer", "__wasm_set_stack_pointer")
  addTlsBaseFunctions(store, $$"$root", "[context-get-1]", "[context-set-1]")
  addTlsBaseFunctions(store, "env", "__wasm_get_tls_base", "__wasm_set_tls_base")

  store.addFunction(
    HostFunction(
      $$"$root",
      "[waitable-set-new]",
      FunctionType.of(
        listOf(),
        listOf(ValType.I32),
      ),
      WasmFunctionHandle { instance, args ->
        val waitableSet = WaitableSet()
        waitableSet.rep = putImportedResource(waitableSet)
        waitableSet.hasRep = true
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
        taskCancel()
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
        val subtaskStateOrNone = subtaskCancel(args[0].toInt())
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
        subtaskDrop(args[0].toInt())
        longArrayOf()
      },
    ),
  )
}

private fun HostBridge.addStackPointerFunctions(
  store: Store,
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
        val result = currentThread.stackPointer
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
        currentThread.stackPointer = args[0].toInt()
        longArrayOf()
      },
    ),
  )
}

private fun HostBridge.addTlsBaseFunctions(
  store: Store,
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
        val result = currentThread.tlsBase
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
        currentThread.tlsBase = args[0].toInt()
        longArrayOf()
      },
    ),
  )
}
