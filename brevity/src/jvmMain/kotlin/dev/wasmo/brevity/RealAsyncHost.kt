@file:OptIn(BrevityInternalApi::class)

package dev.wasmo.brevity

@BrevityInternalApi
class RealAsyncHost : Async.Host {
  lateinit var bridge: HostBridge

  override fun waitableSetNew() = HostWaitableSet(bridge)

  override fun taskCancel() {
    TODO("Not yet implemented")
  }

  override fun subtaskCancel(subtask: Int): Int {
    TODO("Not yet implemented")
  }

  override fun subtaskDrop(subtask: Int) {
    // TODO("Not yet implemented")
  }
}
