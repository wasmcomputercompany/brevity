package dev.wasmo.brevity

import kotlinx.coroutines.selects.SelectBuilder

@BrevityInternalApi
interface Waitable {
  val index: Int
  fun poll(): Event

  fun SelectBuilder<Event>.onEvent()
}
