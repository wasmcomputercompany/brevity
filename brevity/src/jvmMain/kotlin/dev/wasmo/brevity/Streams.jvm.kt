@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:OptIn(BrevityInternalApi::class)

package dev.wasmo.brevity

import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine

actual class FuturePair<T> actual constructor() {
  actual val send = SendFuture(this)
  actual val receive = ReceiveFuture(this)

  internal var binding: StreamBinding<T>? = null

  actual operator fun component1() = send
  actual operator fun component2() = receive
}

actual class SendFuture<T> internal actual constructor(
  private val pair: FuturePair<T>,
) : Waitable() {
  actual suspend fun complete(value: T) {
    TODO()
  }

  @BrevityInternalApi
  fun write(address: Int): Int {
    return PackedCopyResult.Blocked.value.toInt()
  }

  @BrevityInternalApi
  fun cancel(): Int {
    TODO()
  }
}

actual class ReceiveFuture<T> internal actual constructor(
  private val pair: FuturePair<T>,
) : Waitable() {
  actual suspend fun await(): T {
    TODO()
  }

  @BrevityInternalApi
  fun initBinding(bridge: HostBridge, binding: StreamBinding<T>) {
  }

  @BrevityInternalApi
  fun read(address: Int): Int {
    TODO()
  }

  @BrevityInternalApi
  fun cancel(): Int {
    TODO()
  }
}

actual class SendStream<T> internal actual constructor(
  private val pair: StreamPair<T>,
) : Waitable() {
  internal lateinit var buffer: Buffer<T>

  internal var continuation: CancellableContinuation<Unit>? = null

  actual suspend fun send(value: T) {
    buffer.write(value)

    pair.binding.transferIfPossible(
      memoryAllocator = pair.memoryAllocator,
      sendBuffer = pair.send.buffer,
      receiveBuffer = pair.receive.buffer,
    )

    if (buffer.pos == 0) {
      suspendCancellableCoroutine { continuation ->
        buffer.continuation = continuation
      }
    }

    check(buffer.pos == 1)
    buffer.queued = false
    buffer.values.clear()
    buffer.continuation = null
  }

  @BrevityInternalApi
  fun write(address: Int, count: Int): Int {
    buffer.setAddressRange(pair.binding, address, count)

    pair.binding.transferIfPossible(
      memoryAllocator = pair.memoryAllocator,
      sendBuffer = pair.send.buffer,
      receiveBuffer = pair.receive.buffer,
    )

    return buffer.takeResult()
  }

  @BrevityInternalApi
  fun cancel(): Int {
    TODO()
  }
}

actual class ReceiveStream<T> internal actual constructor(
  internal val pair: StreamPair<T>,
) : Waitable() {
  internal lateinit var buffer: Buffer<T>

  internal var continuation: CancellableContinuation<T>? = null

  @BrevityInternalApi
  fun initBinding(bridge: HostBridge, binding: StreamBinding<T>) {
    pair.initBinding(bridge, binding)
  }

  @BrevityInternalApi
  fun read(address: Int, count: Int): Int {
    buffer.setAddressRange(pair.binding, address, count)
    pair.binding.transferIfPossible(
      memoryAllocator = pair.memoryAllocator,
      sendBuffer = pair.send.buffer,
      receiveBuffer = pair.receive.buffer,
    )
    return buffer.takeResult()
  }

  actual suspend fun receive(): T {
    buffer.read(1)

    pair.binding.transferIfPossible(
      memoryAllocator = pair.memoryAllocator,
      sendBuffer = pair.send.buffer,
      receiveBuffer = pair.receive.buffer,
    )

    if (buffer.pos == 0) {
      suspendCancellableCoroutine { continuation ->
        buffer.continuation = continuation
      }
    }

    check(buffer.pos == 1)
    buffer.queued = false
    val result = buffer.values.removeFirst()
    buffer.continuation = null
    return result
  }

  @BrevityInternalApi
  fun cancel(): Int {
    TODO()
  }
}

actual class StreamPair<T> {
  actual val send = SendStream(this)
  actual val receive = ReceiveStream(this)

  internal lateinit var memoryAllocator: MemoryAllocator
  internal lateinit var binding: StreamBinding<T>

  actual constructor()

  actual operator fun component1() = send
  actual operator fun component2() = receive

  @BrevityInternalApi
  fun initBinding(bridge: HostBridge, binding: StreamBinding<T>) {
    check(!send.hasRep && !receive.hasRep)
    check(!::binding.isInitialized && !::memoryAllocator.isInitialized)

    send.rep = bridge.putImportedResource(send)
    send.hasRep = true
    send.buffer = Buffer(send, StreamEndId.StreamWrite)

    receive.rep = bridge.putImportedResource(receive)
    receive.hasRep = true
    receive.buffer = Buffer(receive, StreamEndId.StreamRead)

    this.binding = binding
    this.memoryAllocator = bridge.memoryAllocator
  }
}

@BrevityInternalApi
abstract class StreamBinding<T> {
  abstract val alignment: Int
  abstract val byteCount: Int
  abstract fun store(memoryAllocator: MemoryAllocator, address: Int, value: T)
  abstract fun load(address: Int): T

  internal fun transferIfPossible(
    memoryAllocator: MemoryAllocator,
    sendBuffer: Buffer<T>,
    receiveBuffer: Buffer<T>,
  ) {
    val sendAvailable = sendBuffer.available
    val receiveAvailable = receiveBuffer.available
    when {
      // Suspend until there's both a send and a receive queued up.
      !receiveBuffer.queued || !sendBuffer.queued -> return

      sendAvailable > 0 && receiveAvailable > 0 -> {
        val toTransfer = minOf(sendAvailable, receiveAvailable)
        transfer(memoryAllocator, sendBuffer, receiveBuffer, toTransfer)

        sendBuffer.queued = (toTransfer < sendAvailable)
        sendBuffer.onProgress(toTransfer)

        receiveBuffer.queued = (toTransfer < receiveAvailable)
        receiveBuffer.onProgress(receiveBuffer.pos)
      }

      // If the sender did a zero-length send, notify the sender. Note that if BOTH do a zero length
      // op, we notify only the sender. This is asymmetric!
      sendAvailable == 0 -> {
        sendBuffer.queued = false
        sendBuffer.onProgress(0)
      }

      // If the receiver did a zero-length receive, notify the receiver.
      else -> {
        receiveBuffer.queued = false
        receiveBuffer.onProgress(0)
      }
    }
  }

  internal fun transfer(
    memoryAllocator: MemoryAllocator,
    sendBuffer: Buffer<T>,
    receiveBuffer: Buffer<T>,
    count: Int,
  ) {
    val byteCount = byteCount
    var sendAddress = sendBuffer.address + (sendBuffer.pos * byteCount)
    var receiveAddress = receiveBuffer.address + (receiveBuffer.pos * byteCount)

    for (p in 0 until count) {
      val value = when (sendBuffer.address) {
        ADDRESS_LIST -> sendBuffer.values[sendBuffer.pos + p]
        else -> load(sendAddress)
          .also { sendAddress += byteCount }
      }

      when (receiveBuffer.address) {
        ADDRESS_LIST -> receiveBuffer.values += value
        else -> store(memoryAllocator, receiveAddress, value)
          .also { receiveAddress += byteCount }
      }
    }

    sendBuffer.pos += count
    receiveBuffer.pos += count
  }
}

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

  fun write(value: T) {
    check(values.isEmpty())
    check(!queued)

    this.queued = true
    this.address = ADDRESS_LIST
    this.pos = 0
    this.values += value
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
private const val ADDRESS_LIST = -1
