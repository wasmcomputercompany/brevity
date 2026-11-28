package dev.wasmo.brevity

import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

@BrevityInternalApi
internal class StreamEnd<T>(
  private val waitable: Waitable,
  private val streamEndId: StreamEndId,
) {
  lateinit var other: StreamEnd<T>
  lateinit var binding: StreamBinding<T>
  lateinit var memoryAllocator: MemoryAllocator

  /** A continuation to unblock after accessing the deque. */
  private var continuation: Continuation<Unit>? = null

  /** -1 if there's no transfer in progress. */
  private var pos = -1
  private var limit = -1
  private var resumed = false

  /** The current batch of data. */
  private var data: Data<T>? = null

  private val sender: StreamEnd<T>
    get() = when (streamEndId) {
      StreamEndId.StreamRead, StreamEndId.FutureRead -> other
      StreamEndId.StreamWrite, StreamEndId.FutureWrite -> this
    }

  private val receiver: StreamEnd<T>
    get() = when (streamEndId) {
      StreamEndId.StreamRead, StreamEndId.FutureRead -> this
      StreamEndId.StreamWrite, StreamEndId.FutureWrite -> other
    }

  var closed = false
    private set

  suspend fun hostTransfer(
    data: Data<T>,
    count: Int,
    rendezvous: Boolean = false,
  ): Int {
    check(!closed) { "closed" }
    if (pos != -1) throw ConcurrentModificationException()
    check(this.data == null)

    if (other.closed) return 0
    if (count == 0 && !rendezvous) return 0

    this.data = data
    this.pos = 0
    this.limit = count.coerceAtMost(PackedCopyResult.MAX_PROGRESS)

    // If both ends are State.Transferring, transfer!
    if (other.pos != -1) {
      transferAndResume(
        sender = sender,
        receiver = receiver,
      )
    }

    // Suspend if nothing was transferred.
    if (!resumed) {
      suspendCancellableCoroutine { continuation ->
        this.continuation = continuation
      }
    }

    val result = this.pos

    // Transfer complete: become idle again.
    this.pos = -1
    this.limit = -1
    this.resumed = false
    this.data = null

    return result
  }

  fun guestTransfer(
    address: Int,
    count: Int,
  ): Int {
    require(address.alignTo(binding.alignment) == address)
    check(!closed) { "closed" }
    if (pos != -1) throw ConcurrentModificationException()

    if (other.closed) {
      return PackedCopyResult(CopyResult.Dropped).value.toInt()
    }

    this.data = GuestMemory(address)
    this.pos = 0
    this.limit = count.coerceAtMost(PackedCopyResult.MAX_PROGRESS)

    // If both ends are State.Transferring, transfer!
    if (other.pos != -1) {
      transferAndResume(
        sender = sender,
        receiver = receiver,
      )
    }

    return takeQueuedEventOrBlocked().p2
  }

  private fun resume() {
    resumed = true

    if (data !is StreamEnd.GuestMemory) {
      val continuation = this.continuation ?: return
      this.continuation = null
      continuation.resume(Unit)
      return
    }

    waitable.enqueueEvent {
      val packedCopyResult = PackedCopyResult(CopyResult.Completed, pos)

      // Become idle again.
      this.pos = -1
      this.limit = -1
      this.resumed = false
      this.data = null

      Event.Stream(streamEndId, waitable.rep, packedCopyResult)
    }
  }

  private fun takeQueuedEventOrBlocked(): Event {
    var result = waitable.take()
    if (result == Event.None) {
      result = Event.Stream(streamEndId, waitable.rep, PackedCopyResult.Blocked)
    }
    return result
  }

  fun close() {
    if (pos != -1) throw ConcurrentModificationException()
    this.closed = true
  }

  private companion object {
    /** Transfers from this sender to [receiver]. */
    @OptIn(BrevityInternalApi::class)
    fun <T> transferAndResume(sender: StreamEnd<T>, receiver: StreamEnd<T>) {
      val senderPos = sender.pos
      val senderLimit = sender.limit
      val receiverPos = receiver.pos
      val receiverLimit = receiver.limit
      check(senderPos != -1 && receiverPos != -1)

      val sendAvailable = senderLimit - senderPos
      val receiveAvailable = receiverLimit - receiverPos
      when {
        sendAvailable > 0 && receiveAvailable > 0 -> {
          val toTransfer = minOf(sendAvailable, receiveAvailable)
          transfer(sender, receiver, toTransfer)
          sender.resume()
          receiver.resume()
        }

        // If the sender did a zero-length send, notify the sender. Note that if BOTH do a zero
        // length op, we notify only the sender. This is asymmetric!
        sendAvailable == 0 -> sender.resume()

        // If the receiver did a zero-length receive, notify the receiver.
        else -> receiver.resume()
      }
    }

    /** Transfer [count] elements from sender to receiver. */
    @OptIn(BrevityInternalApi::class)
    fun <T> transfer(
      sender: StreamEnd<T>,
      receiver: StreamEnd<T>,
      count: Int,
    ) {
      val source = sender.data!!
      val sink = receiver.data!!
      when {
        // Guest to host.
        source is StreamEnd.GuestMemory -> {
          sink.load(
            source = source,
            sourcePos = sender.pos,
            sinkPos = receiver.pos,
            count = count,
          )
        }

        // Host to guest.
        sink is StreamEnd.GuestMemory -> {
          source.store(
            sourcePos = sender.pos,
            sink = sink,
            sinkPos = receiver.pos,
            count = count,
          )
        }

        // Host to host.
        else -> {
          source.transfer(
            sourcePos = sender.pos,
            sink = sink,
            sinkPos = receiver.pos,
            count = count,
          )
        }
      }

      sender.pos += count
      receiver.pos += count
    }
  }

  abstract class Data<T> {
    abstract fun get(pos: Int): T
    abstract fun put(pos: Int, value: T)

    open fun transfer(
      sourcePos: Int,
      sink: Data<T>,
      sinkPos: Int,
      count: Int,
    ) {
      for (i in 0 until count) {
        sink.put(sinkPos + i, get(sourcePos + i))
      }
    }

    open fun store(
      sourcePos: Int,
      sink: StreamEnd<T>.GuestMemory,
      sinkPos: Int,
      count: Int,
    ) {
      for (i in 0 until count) {
        sink.put(sinkPos + i, get(sourcePos + i))
      }
    }

    open fun load(
      source: StreamEnd<T>.GuestMemory,
      sourcePos: Int,
      sinkPos: Int,
      count: Int,
    ) {
      for (i in 0 until count) {
        put(sinkPos + i, source.get(sourcePos + i))
      }
    }
  }

  /** This is an inner class because it needs to know *which memory* it's accessing. */
  inner class GuestMemory(
    private val address: Int,
  ) : Data<T>() {
    override fun get(pos: Int): T {
      return binding.load(
        address = address + (pos * binding.byteCount),
      )
    }

    override fun put(pos: Int, value: T) {
      binding.store(
        memoryAllocator = memoryAllocator,
        address = address + (pos * binding.byteCount),
        value = value,
      )
    }
  }

  class ListSource<T>(
    val list: List<T>,
  ) : Data<T>() {
    override fun get(pos: Int): T = list[pos]

    override fun put(pos: Int, value: T) {
      error("cannot write a source")
    }
  }

  class ListSink<T>(
    val list: MutableList<T>,
  ) : Data<T>() {
    override fun get(pos: Int): T {
      error("cannot read a sink")
    }

    override fun put(pos: Int, value: T) {
      list += value
    }
  }

  object UnitData : Data<Unit>() {
    override fun get(pos: Int) = Unit

    override fun put(pos: Int, value: Unit) {
    }

    override fun transfer(
      sourcePos: Int,
      sink: Data<Unit>,
      sinkPos: Int,
      count: Int,
    ) {
      if (sink == this) return
      for (i in sinkPos until sinkPos + count) {
        sink.put(i, Unit)
      }
    }

    override fun store(
      sourcePos: Int,
      sink: StreamEnd<Unit>.GuestMemory,
      sinkPos: Int,
      count: Int,
    ) {
    }

    override fun load(
      source: StreamEnd<Unit>.GuestMemory,
      sourcePos: Int,
      sinkPos: Int,
      count: Int,
    ) {
    }
  }
}
