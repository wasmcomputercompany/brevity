package dev.wasmo.brevity

import kotlinx.coroutines.yield

@BrevityInternalApi
abstract class Thread {
  var stackPointer: Int = 0
  var tlsBase: Int = 0
}

/**
 * The thread used when an async task isn't currently running. For example, this is used when a
 * synchronous task is running.
 */
@BrevityInternalApi
class DefaultThread : Thread() {
  override fun toString() = "DefaultThread"
}

/**
 * A thread that belongs to a launched task.
 *
 * Launch a task by calling an async function, which creates its initial thread. The task may spawn
 * additional threads that all service the same task.
 */
@BrevityInternalApi
class TaskThread internal constructor(
  val name: String,
  val bridge: HostBridge,
  val task: Task<*>,
) : Thread() {
  suspend fun execute(
    execute: () -> PackedAsyncResult,
    callback: (Event) -> PackedAsyncResult,
  ) {
    var packedAsyncResult = bridge.executeThread(this) {
      execute()
    }

    while (true) {
      val event = when (packedAsyncResult.callbackCode) {
        CallbackCode.Exit -> {
          task.cancel()
          return
        }

        CallbackCode.Yield -> {
          yield()
          Event.None
        }

        CallbackCode.Wait -> {
          val waitableSet = bridge.getImportedResource<WaitableSet>(
            packedAsyncResult.waitableSetIndex,
          )
          waitableSet.waitForEvent()
        }

        CallbackCode.Max -> TODO()
      }

      packedAsyncResult = bridge.executeThread(this) {
        callback(event)
      }
    }
  }

  override fun toString() = name
}
