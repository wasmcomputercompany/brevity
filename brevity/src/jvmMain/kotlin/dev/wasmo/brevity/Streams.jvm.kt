@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:OptIn(BrevityInternalApi::class)

package dev.wasmo.brevity

import kotlinx.coroutines.CancellableContinuation

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
    TODO()
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

actual class StreamPair<T> {
  actual val send = SendStream(this)
  actual val receive = ReceiveStream(this)

  internal lateinit var binding: StreamBinding<T>

  actual constructor()

  actual operator fun component1() = send
  actual operator fun component2() = receive

  @BrevityInternalApi
  fun initBinding(bridge: HostBridge, binding: StreamBinding<T>) {
    check(!send.hasRep && !receive.hasRep && !::binding.isInitialized)
    send.rep = bridge.putImportedResource(send)
    send.hasRep = true
    send.buffer = Buffer()
    receive.rep = bridge.putImportedResource(receive)
    receive.hasRep = true
    receive.buffer = Buffer()
    this.binding = binding
  }
}

actual class SendStream<T> internal actual constructor(
  private val pair: StreamPair<T>,
) : Waitable() {
  @BrevityInternalApi
  internal lateinit var buffer: Buffer<T>

  internal var continuation: CancellableContinuation<Unit>? = null

  actual suspend fun send(value: T) {
  }

  @BrevityInternalApi
  fun write(address: Int, count: Int): Int {
    // TODO: address alignment check
    // TODO: count bounds check (2**28)
    check(!buffer.queued)

    buffer.queued = true
    buffer.address = address
    buffer.count = count
    buffer.progress = 0

    transfer(this, pair.receive)

    var event = take()
    if (event == Event.None) event = Event.StreamWrite(rep, PackedCopyResult.Blocked)
    return event.p2
  }

  @BrevityInternalApi
  fun onProgress(progress: Int) {
    enqueueEvent {
      // TODO: immediately set buffer.queued = false
      // TODO: handle dropped, canceling state
      Event.StreamWrite(rep, PackedCopyResult(CopyResult.Completed, progress))
    }
  }

  @BrevityInternalApi
  fun cancel(): Int {
    TODO()
  }
}

actual class ReceiveStream<T> internal actual constructor(
  private val pair: StreamPair<T>,
) : Waitable() {
  @BrevityInternalApi
  internal lateinit var buffer: Buffer<T>

  internal var continuation: CancellableContinuation<T>? = null

  @BrevityInternalApi
  fun initBinding(bridge: HostBridge, binding: StreamBinding<T>) {
    pair.initBinding(bridge, binding)
  }

  @BrevityInternalApi
  fun read(address: Int, count: Int): Int {
    return Event.StreamRead(rep, PackedCopyResult.Blocked).p2
  }

  actual suspend fun receive(): T {
    TODO()
  }

  @BrevityInternalApi
  fun onProgress(progress: Int) {
    enqueueEvent {
      Event.StreamRead(rep, PackedCopyResult(CopyResult.Completed, progress))
    }
  }

  @BrevityInternalApi
  fun cancel(): Int {
    TODO()
  }
}

@BrevityInternalApi
abstract class StreamBinding<T> {
  abstract val byteCount: Int
  abstract fun store(memoryAllocator: MemoryAllocator, address: Int, value: T)
  abstract fun load(address: Int): T
}

@BrevityInternalApi
internal class Buffer<T> {
  var queued = false
  var address: Int = 0
  var count: Int = 0
  var progress: Int = 0
  val available: Int
    get() = count - progress
}

@BrevityInternalApi
internal fun <T> transfer(
  sendStream: SendStream<T>,
  receiveStream: ReceiveStream<T>,
) {
  // Suspend until there's both a send and a receive queued up.
  if (!receiveStream.buffer.queued || !sendStream.buffer.queued) return

  val sendAvailable = sendStream.buffer.available
  val receiveAvailable = receiveStream.buffer.available
  when {
    sendAvailable > 0 && receiveAvailable > 0 -> {
      val toTransfer = minOf(sendAvailable, receiveAvailable)
      transfer(sendStream.buffer, receiveStream.buffer, toTransfer)

      sendStream.buffer.queued = (toTransfer < sendAvailable)
      sendStream.onProgress(toTransfer)

      receiveStream.buffer.queued = (toTransfer < receiveAvailable)
      receiveStream.onProgress(receiveStream.buffer.progress)
    }

    // If the sender did a zero-length send, notify the sender. Note that if BOTH do a zero length
    // op, we notify only the sender. This is asymmetric!
    sendAvailable == 0 -> {
      sendStream.buffer.queued = false
      sendStream.onProgress(0)
    }

    // If the receiver did a zero-length receive, notify the receiver.
    else -> {
      receiveStream.buffer.queued = false
      receiveStream.onProgress(0)
    }
  }
}

@BrevityInternalApi
internal fun <T> transfer(sendBuffer: Buffer<T>, receiveBuffer: Buffer<T>, count: Int) {
  // TODO: transfer, including allocating data on the guest
}
