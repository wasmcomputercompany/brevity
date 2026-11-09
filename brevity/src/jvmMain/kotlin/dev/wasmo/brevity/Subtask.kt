package dev.wasmo.brevity

import kotlinx.coroutines.Job
import kotlinx.coroutines.selects.SelectBuilder

@BrevityInternalApi
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
