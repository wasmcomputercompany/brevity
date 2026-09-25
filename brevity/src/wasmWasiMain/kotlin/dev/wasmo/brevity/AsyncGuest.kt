package dev.wasmo.brevity

@BrevityInternalApi
val Async.host: Async.Host
  get() = AsyncHost

@BrevityInternalApi
private object AsyncHost : Async.Host {
  override fun waitableSetNew() = GuestWaitableSet(waitableSetNew_export())

  override fun <T> contextGet(index: Int, typeId: TypeId<T>): T {
    require(typeId == TypeId.I32)
    return when (index) {
      0 -> contextGet0_export() as T
      1 -> contextGet1_export() as T
      else -> error("unexpected index: $index")
    }
  }

  override fun <T> contextSet(index: Int, typeId: TypeId<T>, value: T) {
    require(typeId == TypeId.I32)
    when (index) {
      0 -> contextSet0_export(0, value as Int)
      1 -> contextSet1_export(0, value as Int)
      else -> error("unexpected index: $index")
    }
  }

  override fun taskCancel() {
    taskCancel_export()
  }
}

@BrevityInternalApi
private class GuestWaitableSet(
  private val waitableSetIndex: Int,
) : WaitableSet {
  override fun join(waitableIndex: Int) {
    waitableJoin_export(waitableIndex, waitableSetIndex)
  }

  override fun poll(eventPointer: Int): Int {
    return waitableSetPoll_export(waitableSetIndex, eventPointer)
  }

  override fun close() {
    waitableSetDrop_export(waitableSetIndex)
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
internal external fun contextSet0_export(type: Int, value: Int)

@WasmImport(module = $$"$root", name = "[context-set-1]")
internal external fun contextSet1_export(type: Int, value: Int)

@WasmImport(module = $$"[export]$root", name = "[task-cancel]")
internal external fun taskCancel_export()
