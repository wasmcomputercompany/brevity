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

  private var state = State.Idle

  /** Data is either in memory at [address]... */
  private var address = 0

  /** ...or it is in the [values] deque, in which case [address] is [ADDRESS_LIST]. */
  private val values = ArrayDeque<T>()

  /** A continuation to unblock after accessing the deque. */
  private var continuation: Continuation<Unit>? = null

  private var pos = 0
  private var limit = 0
  private val available: Int
    get() = limit - pos

  val closed: Boolean
    get() = state == State.Closed

  suspend fun hostWrite(
    receiver: End<T>,
    values: List<T>,
  ): Int {
    when (state) {
      State.Idle -> {}
      State.Transferring -> throw ConcurrentModificationException()
      State.Closed -> throw IllegalStateException("closed")
    }
    check(this.values.isEmpty())

    val receiverState = receiver.state
    if (receiverState == State.Closed) return 0

    this.state = State.Transferring
    this.address = ADDRESS_LIST
    this.pos = 0
    this.values += values
    this.limit = values.size.coerceAtMost(PackedCopyResult.MAX_PROGRESS)

    // If both ends are State.Transferring, transfer!
    if (receiverState == State.Transferring) {
      transferAndResume(
        sender = this,
        receiver = receiver,
      )
    }

    // Suspend if nothing was transferred.
    if (pos == 0) {
      suspendCancellableCoroutine { continuation ->
        this.continuation = continuation
      }
      this.continuation = null
    }

    val result = this.pos

    // Write complete: become idle again.
    this.state = State.Idle
    this.pos = 0
    this.limit = 0
    this.values.clear()

    return result
  }

  suspend fun hostRead(
    sender: End<T>,
    count: Int,
  ): List<T> {
    when (state) {
      State.Idle -> {}
      State.Transferring -> throw ConcurrentModificationException()
      State.Closed -> throw IllegalStateException("closed")
    }
    check(this.values.isEmpty())

    this.state = State.Transferring
    this.address = ADDRESS_LIST
    this.pos = 0
    this.limit = count.coerceAtMost(PackedCopyResult.MAX_PROGRESS)

    val senderState = sender.state
    if (senderState == State.Closed) return listOf()

    // If both ends are State.Transferring, transfer!
    if (senderState == State.Transferring) {
      transferAndResume(
        sender = sender,
        receiver = this,
      )
    }

    // Suspend if nothing was transferred.
    if (pos == 0) {
      suspendCancellableCoroutine { continuation ->
        this.continuation = continuation
      }
      this.continuation = null
    }

    val result = values.toList()

    // Read complete: become idle again.
    this.state = State.Idle
    this.pos = 0
    this.limit = 0
    this.values.clear()

    return result
  }

  fun guestWrite(
    receiver: End<T>,
    address: Int,
    count: Int,
  ): Int {
    require(address.alignTo(binding.alignment) == address)
    when (state) {
      State.Idle -> {}
      State.Transferring -> throw ConcurrentModificationException()
      State.Closed -> throw IllegalStateException("closed")
    }

    val receiverState = receiver.state
    if (receiverState == State.Closed) {
      return PackedCopyResult(CopyResult.Dropped).value.toInt()
    }

    this.state = State.Transferring
    this.address = address
    this.pos = 0
    this.limit = count.coerceAtMost(PackedCopyResult.MAX_PROGRESS)

    // If both ends are State.Transferring, transfer!
    if (receiverState == State.Transferring) {
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
    when (state) {
      State.Idle -> {}
      State.Transferring -> throw ConcurrentModificationException()
      State.Closed -> throw IllegalStateException("closed")
    }

    val senderState = sender.state
    if (senderState == State.Closed) {
      return PackedCopyResult(CopyResult.Dropped).value.toInt()
    }

    this.state = State.Transferring
    this.address = address
    this.pos = 0
    this.limit = count.coerceAtMost(PackedCopyResult.MAX_PROGRESS)

    // If both ends are State.Transferring, transfer!
    if (senderState == State.Transferring) {
      transferAndResume(
        sender = sender,
        receiver = this,
      )
    }

    return takeQueuedEventOrBlocked().p2
  }

  private fun resume() {
    val continuation = this.continuation
    if (continuation != null) {
      continuation.resume(Unit)
      return
    }

    waitable.enqueueEvent {
      val packedCopyResult = PackedCopyResult(CopyResult.Completed, pos)

      // Become idle again.
      this.state = State.Idle
      this.pos = 0
      this.limit = 0
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
    when (state) {
      State.Idle -> {}
      State.Transferring -> throw ConcurrentModificationException()
      State.Closed -> return
    }

    this.state = State.Closed
  }

  private companion object {
    /** Sentinel value to read items from a list. */
    const val ADDRESS_LIST = -1

    /** Transfers from this sender to [receiver]. */
    @OptIn(BrevityInternalApi::class)
    fun <T> transferAndResume(sender: End<T>, receiver: End<T>) {
      check(sender.state == State.Transferring && receiver.state == State.Transferring)

      val sendAvailable = sender.available
      val receiveAvailable = receiver.available
      when {
        sendAvailable > 0 && receiveAvailable > 0 -> {
          val toTransfer = minOf(sendAvailable, receiveAvailable)
          transfer(sender, receiver, toTransfer)

          if (toTransfer == sendAvailable) {
            sender.state = State.Idle
          }
          sender.resume()

          if (toTransfer == receiveAvailable) {
            receiver.state = State.Idle
          }
          receiver.resume()
        }

        // If the sender did a zero-length send, notify the sender. Note that if BOTH do a zero length
        // op, we notify only the sender. This is asymmetric!
        sendAvailable == 0 -> {
          sender.state = State.Idle
          sender.resume()
        }

        // If the receiver did a zero-length receive, notify the receiver.
        else -> {
          receiver.state = State.Idle
          receiver.resume()
        }
      }
    }

    @OptIn(BrevityInternalApi::class)
    fun <T> transfer(
      sender: End<T>,
      receiver: End<T>,
      count: Int,
    ) {
      val byteCount = when {
        sender.address == ADDRESS_LIST && receiver.address == ADDRESS_LIST -> 0
        else -> sender.binding.byteCount
      }
      var sendAddress = sender.address + (sender.pos * byteCount)
      var receiveAddress = receiver.address + (receiver.pos * byteCount)

      for (p in 0 until count) {
        val value = when (sender.address) {
          ADDRESS_LIST -> sender.values[sender.pos + p]
          else -> sender.binding.load(sendAddress)
            .also { sendAddress += byteCount }
        }

        when (receiver.address) {
          ADDRESS_LIST -> receiver.values += value
          else -> sender.binding.store(sender.memoryAllocator, receiveAddress, value)
            .also { receiveAddress += byteCount }
        }
      }

      sender.pos += count
      receiver.pos += count
    }
  }

  internal enum class State {
    Idle,
    Transferring,
    Closed,
  }
}
