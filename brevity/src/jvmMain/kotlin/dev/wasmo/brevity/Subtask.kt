package dev.wasmo.brevity

import kotlinx.coroutines.Job
import kotlinx.coroutines.selects.SelectBuilder

@BrevityInternalApi
class Subtask(
  override val index: Int,
  val job: Job,
) : Waitable {
  override fun poll(): Event {
    return Event.None
  }

  override fun SelectBuilder<Event>.onEvent() {
    job.onJoin {
      Event.Subtask(index, SubtaskState.Returned)
    }
  }
}
