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
        val waitable = bridge.waitables[waitableIndex] ?: error("no waitable: $waitableIndex")
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
          val waitable = bridge.waitables[waitableIndex] ?: error("no waitable: $waitableIndex")
          waitable.select(this@select)
        }
      }
    }
  }

  class Waitable(
    val index: Int,
    val job: Job,
  ) {
    fun poll(): Event {
      return Event.None
    }

    fun select(selectBuilder: SelectBuilder<Event>) {
      with(selectBuilder) {
        job.onJoin {
          Event.Subtask(index, SubtaskState.Returned)
        }
      }
    }
  }
}
