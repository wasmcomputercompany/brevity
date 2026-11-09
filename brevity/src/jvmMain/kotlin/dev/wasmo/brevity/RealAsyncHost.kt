@file:OptIn(BrevityInternalApi::class)

package dev.wasmo.brevity

import kotlinx.coroutines.Job
import kotlinx.coroutines.selects.SelectBuilder
import kotlinx.coroutines.selects.select

@BrevityInternalApi
class RealAsyncHost : Async.Host {
  lateinit var bridge: HostBridge

  override fun waitableSetNew(): RealWaitableSet {
    return RealWaitableSet()
  }

  override fun taskCancel() {
    TODO("Not yet implemented")
  }

  override fun subtaskCancel(subtask: Int): Int {
    TODO("Not yet implemented")
  }

  override fun subtaskDrop(subtask: Int) {
    // TODO("Not yet implemented")
  }

  inner class RealWaitableSet : WaitableSet() {
    val waitableIndexes = ArrayDeque<Int>()

    override fun join(waitableIndex: Int) {
      waitableIndexes += waitableIndex
    }

    override fun poll(): Event {
      for (waitableIndex in waitableIndexes) {
        val waitable = bridge.getImportedResource(waitableIndex) as? Waitable
          ?: error("no waitable: $waitableIndex")
        val event = waitable.poll()
        if (event != Event.None) return event
      }
      return Event.None
    }

    override fun wait(): Event {
      error("not implemented")
    }

    /** Returns an arbitrary waitable that's ready, suspending if necessary. */
    suspend fun waitForEvent(): Event {
      return select {
        for (waitableIndex in waitableIndexes) {
          val waitable = bridge.getImportedResource(waitableIndex) as? Waitable
            ?: error("no waitable: $waitableIndex")
          waitable.select(this@select)
        }
      }
    }
  }

  interface Waitable {
    fun poll(): Event
    fun select(selectBuilder: SelectBuilder<Event>)
  }

  class Subtask(
    val job: Job,
  ) : Resource(), Waitable {
    override fun poll(): Event {
      return Event.None
    }

    override fun select(selectBuilder: SelectBuilder<Event>) {
      with(selectBuilder) {
        job.onJoin {
          Event.Subtask(rep, SubtaskState.Returned)
        }
      }
    }
  }
}
