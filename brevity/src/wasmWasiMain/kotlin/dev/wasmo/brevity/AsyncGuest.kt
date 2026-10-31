@file:OptIn(ExperimentalWasmInterop::class)

package dev.wasmo.brevity

@BrevityInternalApi
val Async.host: Async.Host
  get() = AsyncHost

@BrevityInternalApi
private object AsyncHost : Async.Host {
  override fun waitableSetNew(): GuestWaitableSet {
    return GuestWaitableSet().apply {
      rep = waitableSetNew_export()
      hasRep = true
    }
  }

  override fun taskCancel() {
    taskCancel_export()
  }

  override fun subtaskCancel(subtask: Int): Int {
    return subtaskCancel_export(subtask)
  }

  override fun subtaskDrop(subtask: Int) {
    subtaskDrop_export(subtask)
  }
}

@BrevityInternalApi
private class GuestWaitableSet : WaitableSet() {
  override fun join(waitableIndex: Int) {
    waitableJoin_export(waitableIndex, rep)
  }

  override fun poll(eventPointer: Int): Int {
    return waitableSetPoll_export(rep, eventPointer)
  }

  override fun close() {
    waitableSetDrop_export(rep)
  }

  override fun wait(): Event {
    TODO("Not yet implemented")
  }
}

@WasmImport(module = $$"$root", name = "[waitable-set-new]")
internal external fun waitableSetNew_export(): Int

@WasmImport(module = $$"$root", name = "[waitable-join]")
internal external fun waitableJoin_export(waitableIndex: Int, waitableSet: Int)

@WasmImport(module = $$"$root", name = "[waitable-set-poll]")
internal external fun waitableSetPoll_export(waitableSet: Int, eventPointer: Int): Int

@WasmImport(module = $$"$root", name = "[waitable-set-drop]")
internal external fun waitableSetDrop_export(waitableSet: Int)

@WasmImport(module = $$"$root", name = "[context-get-0]")
internal external fun contextGet0_export(): Int

@WasmImport(module = $$"$root", name = "[context-get-1]")
internal external fun contextGet1_export(): Int

@WasmImport(module = $$"$root", name = "[context-set-0]")
internal external fun contextSet0_export(value: Int)

@WasmImport(module = $$"$root", name = "[context-set-1]")
internal external fun contextSet1_export(value: Int)

@WasmImport(module = $$"[export]$root", name = "[task-cancel]")
internal external fun taskCancel_export()

@WasmImport(module = $$"$root", name = "[subtask-cancel]")
internal external fun subtaskCancel_export(subtask: Int): Int

@WasmImport(module = $$"$root", name = "[subtask-drop]")
internal external fun subtaskDrop_export(subtask: Int)
