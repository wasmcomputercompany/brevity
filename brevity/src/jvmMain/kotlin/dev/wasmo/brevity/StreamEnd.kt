package dev.wasmo.brevity

import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

@BrevityInternalApi
internal class End<T>(
  private val waitable: Waitable,
  private val streamEndId: StreamEndId,
) {
  lateinit var binding: StreamBinding<T>
  lateinit var memoryAllocator: MemoryAllocator

  /** Data is either in memory at [address]... */
  private var address = 0

  /** ...or it is in the [values] deque, in which case [address] is [ADDRESS_LIST]. */
  private val values = ArrayDeque<T>()

  /** A continuation to unblock after accessing the deque. */
  private var continuation: Continuation<Unit>? = null

  /** -1 if there's no transfer in progress. */
  private var pos = -1
  private var limit = -1
  private var resumed = false

  var closed = false
    private set

  suspend fun hostWrite(
    receiver: End<T>,
    values: List<T>,
    rendezvous: Boolean = false,
  ): Int {
    check(!closed) { "closed" }
    if (pos != -1) throw ConcurrentModificationException()
    check(this.values.isEmpty())

    if (receiver.closed) return 0
    if (values.isEmpty() && !rendezvous) return 0

    this.address = ADDRESS_LIST
    this.pos = 0
    this.values += values
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
    this.values.clear()

    return result
  }

  suspend fun hostRead(
    sender: End<T>,
    count: Int,
    rendezvous: Boolean = false,
  ): List<T> {
    check(!closed) { "closed" }
    if (pos != -1) throw ConcurrentModificationException()
    check(this.values.isEmpty())

    if (sender.closed) return listOf()
    if (count == 0 && !rendezvous) return listOf()

    this.address = ADDRESS_LIST
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

    val result = values.toList()

    // Read complete: become idle again.
    this.pos = -1
    this.limit = -1
    this.resumed = false
    this.values.clear()

    return result
  }

  fun guestWrite(
    receiver: End<T>,
    address: Int,
    count: Int,
  ): Int {
    require(address.alignTo(binding.alignment) == address)
    check(!closed) { "closed" }
    if (pos != -1) throw ConcurrentModificationException()

    if (receiver.closed) {
      return PackedCopyResult(CopyResult.Dropped).value.toInt()
    }

    this.address = address
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
    sender: End<T>,
    address: Int,
    count: Int,
  ): Int {
    require(address.alignTo(binding.alignment) == address)
    check(!closed) { "closed" }
    if (pos != -1) throw ConcurrentModificationException()

    if (sender.closed) {
      return PackedCopyResult(CopyResult.Dropped).value.toInt()
    }

    this.address = address
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

    if (address == ADDRESS_LIST) {
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
      this.values.clear()

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
    /** Sentinel value to read items from a list. */
    const val ADDRESS_LIST = -1

    /** Transfers from this sender to [receiver]. */
    @OptIn(BrevityInternalApi::class)
    fun <T> transferAndResume(sender: End<T>, receiver: End<T>) {
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
     * We don't resolve [End.binding] for list-to-list transfers, as it's unlikely to be initialized
     * in that case.
     */
    @OptIn(BrevityInternalApi::class)
    fun <T> transfer(
      sender: End<T>,
      receiver: End<T>,
      count: Int,
    ) {
      if (sender.address == ADDRESS_LIST && receiver.address == ADDRESS_LIST) {
        // List to List.
        receiver.values.addAll(sender.values.subList(sender.pos, sender.pos + count))
      } else {
        val binding = sender.binding
        val memoryAllocator = sender.memoryAllocator
        val byteCount = binding.byteCount

        if (sender.address == ADDRESS_LIST) {
          // List to linear memory.
          var r = receiver.address + (receiver.pos * byteCount)
          for (p in 0 until count) {
            val value = sender.values[sender.pos + p]
            binding.store(memoryAllocator, r, value)
            r += byteCount
          }

        } else if (receiver.address == ADDRESS_LIST) {
          // Linear memory to list.
          var s = sender.address + (sender.pos * byteCount)
          for (p in 0 until count) {
            receiver.values += binding.load(s)
            s += byteCount
          }
        } else {
          // Linear memory to linear memory.
          var s = sender.address + (sender.pos * byteCount)
          var r = receiver.address + (receiver.pos * byteCount)
          for (p in 0 until count) {
            val value = binding.load(s)
            binding.store(memoryAllocator, r, value)
            s += byteCount
            r += byteCount
          }
        }
      }
      sender.pos += count
      receiver.pos += count
    }
  }
}
