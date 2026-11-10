package dev.wasmo.brevity

import kotlinx.coroutines.selects.SelectBuilder

@BrevityInternalApi
interface Waitable {
  fun poll(): Event
  fun select(selectBuilder: SelectBuilder<Event>)
}
