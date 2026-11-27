package dev.wasmo.brevity

import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

@BrevityInternalApi
internal class StreamEnd<T>(
  private val waitable: Waitable,
  private val streamEndId: StreamEndId,
) {
  lateinit var binding: StreamBinding<T>
  lateinit var memoryAllocator: MemoryAllocator

  /** Access to the current batch of data. */
  private var data: Data<T>? = null

  /** A continuation to unblock after accessing the deque. */
  private var continuation: Continuation<Unit>? = null

  /** -1 if there's no transfer in progress. */
  private var pos = -1
  private var limit = -1
  private var resumed = false

  var closed = false
    private set

  suspend fun hostWrite(
    receiver: StreamEnd<T>,
    values: List<T>,
    rendezvous: Boolean = false,
  ): Int {
    check(!closed) { "closed" }
    if (pos != -1) throw ConcurrentModificationException()
    check(data == null)

    if (receiver.closed) return 0
    if (values.isEmpty() && !rendezvous) return 0

    this.data = ListSource(values)
    this.pos = 0
    this.limit = values.size.coerceAtMost(PackedCopyResult.MAX_PROGRESS)

    // If both ends are State.Transferring, transfer!
    if (receiver.pos != -1) {
      transferAndResume(
        sender = this,
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

    // Write complete: become idle again.
    this.pos = -1
    this.limit = -1
    this.resumed = false
    this.data = null

    return result
  }

  suspend fun hostRead(
    sender: StreamEnd<T>,
    count: Int,
    rendezvous: Boolean = false,
  ): List<T> {
    check(!closed) { "closed" }
    if (pos != -1) throw ConcurrentModificationException()
    check(data == null)

    if (sender.closed) return listOf()
    if (count == 0 && !rendezvous) return listOf()

    val result = mutableListOf<T>()
    val listSink = ListSink(result)
    this.data = listSink
    this.pos = 0
    this.limit = count.coerceAtMost(PackedCopyResult.MAX_PROGRESS)

    // If both ends are State.Transferring, transfer!
    if (sender.pos != -1) {
      transferAndResume(
        sender = sender,
        receiver = this,
      )
    }

    // Suspend if nothing was transferred.
    if (!resumed) {
      suspendCancellableCoroutine { continuation ->
        this.continuation = continuation
      }
    }

    // Read complete: become idle again.
    this.pos = -1
    this.limit = -1
    this.resumed = false
    this.data = null

    return result
  }

  fun guestWrite(
    receiver: StreamEnd<T>,
    address: Int,
    count: Int,
  ): Int {
    require(address.alignTo(binding.alignment) == address)
    check(!closed) { "closed" }
    if (pos != -1) throw ConcurrentModificationException()

    if (receiver.closed) {
      return PackedCopyResult(CopyResult.Dropped).value.toInt()
    }

    this.data = GuestMemory(address)
    this.pos = 0
    this.limit = count.coerceAtMost(PackedCopyResult.MAX_PROGRESS)

    // If both ends are State.Transferring, transfer!
    if (receiver.pos != -1) {
      transferAndResume(
        sender = this,
        receiver = receiver,
      )
    }

    return takeQueuedEventOrBlocked().p2
  }

  fun guestRead(
    sender: StreamEnd<T>,
    address: Int,
    count: Int,
  ): Int {
    require(address.alignTo(binding.alignment) == address)
    check(!closed) { "closed" }
    if (pos != -1) throw ConcurrentModificationException()

    if (sender.closed) {
      return PackedCopyResult(CopyResult.Dropped).value.toInt()
    }

    this.data = GuestMemory(address)
    this.pos = 0
    this.limit = count.coerceAtMost(PackedCopyResult.MAX_PROGRESS)

    // If both ends are State.Transferring, transfer!
    if (sender.pos != -1) {
      transferAndResume(
        sender = sender,
        receiver = this,
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

    /**
     * Transfer [count] elements from sender to receiver. Each is either a regular [List] or an
     * address in linear memory, for a total of four cases.
     *
     * We don't resolve [StreamEnd.binding] for list-to-list transfers, as it's unlikely to be initialized
     * in that case.
     */
    @OptIn(BrevityInternalApi::class)
    fun <T> transfer(
      sender: StreamEnd<T>,
      receiver: StreamEnd<T>,
      count: Int,
    ) {
      val sourceList = sender.data!!.load(sender.pos, count)
      receiver.data!!.store(receiver.pos, sourceList)

      sender.pos += count
      receiver.pos += count
    }
  }

  sealed interface Data<T> {
    fun load(pos: Int, count: Int): List<T>
    fun store(pos: Int, values: List<T>)
  }

  inner class GuestMemory(
    private val address: Int,
  ) : Data<T> {
    override fun load(
      pos: Int,
      count: Int,
    ): List<T> {
      val byteCount = binding.byteCount
      return List(count) { index ->
        binding.load(address + (pos + index) * byteCount)
      }
    }

    override fun store(
      pos: Int,
      values: List<T>,
    ) {
      val byteCount = binding.byteCount
      for ((index, value) in values.withIndex()) {
        binding.store(
          memoryAllocator = memoryAllocator,
          address = address + (pos + index) * byteCount,
          value = values[index],
        )
      }
    }
  }

  class ListSource<T>(
    val list: List<T>,
  ) : Data<T> {
    override fun load(pos: Int, count: Int) = list.subList(pos, pos + count)

    override fun store(pos: Int, values: List<T>) {
      error("unexpected call")
    }
  }

  class ListSink<T>(
    val list: MutableList<T>,
  ) : Data<T> {
    override fun load(pos: Int, count: Int): List<T> {
      error("unexpected call")
    }

    override fun store(pos: Int, values: List<T>) {
      list += values
    }
  }
}
