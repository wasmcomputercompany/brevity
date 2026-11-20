package dev.wasmo.brevity

import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

@BrevityInternalApi
internal class Buffer<T>(
  val stream: Waitable,
  val streamEndId: StreamEndId,
) {
  var queued = false
  var address = 0
  var pos = 0
  var limit = 0

  val values = ArrayDeque<T>()
  var continuation: Continuation<Unit>? = null

  val available: Int
    get() = limit - pos

  fun setAddressRange(binding: StreamBinding<*>, address: Int, count: Int) {
    require(address.alignTo(binding.alignment) == address)
    require(count <= PackedCopyResult.MAX_PROGRESS)
    check(!queued)

    this.queued = true
    this.address = address
    this.pos = 0
    this.limit = count
  }

  fun write(values: List<T>) {
    check(this.values.isEmpty())
    check(!queued)

    this.queued = true
    this.address = ADDRESS_LIST
    this.pos = 0
    this.values += values
    this.limit = values.size
  }

  fun read(count: Int) {
    check(values.isEmpty())
    check(!queued)

    this.queued = true
    this.address = ADDRESS_LIST
    this.pos = 0
    this.limit = count
  }

  fun onProgress(progress: Int) {
    val continuation = this.continuation
    if (continuation != null) {
      continuation.resume(Unit)
      return
    }

    stream.enqueueEvent {
      queued = false
      values.clear()

      // TODO: handle dropped, canceling state
      Event.Stream(streamEndId, stream.rep, PackedCopyResult(CopyResult.Completed, progress))
    }
  }

  fun takeResult(): Int {
    var event = stream.take()
    if (event == Event.None) {
      event = Event.Stream(streamEndId, stream.rep, PackedCopyResult.Blocked)
    }
    return event.p2
  }
}

/** Sentinel value to read items from a list. */
internal const val ADDRESS_LIST = -1
