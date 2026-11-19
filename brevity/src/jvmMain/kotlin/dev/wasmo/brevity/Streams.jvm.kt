@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:OptIn(BrevityInternalApi::class)

package dev.wasmo.brevity

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine

actual class Stream<T> {
  actual val sender = Sender(this)
  actual val receiver = Receiver(this)

  internal lateinit var memoryAllocator: MemoryAllocator
  internal lateinit var binding: StreamBinding<T>

  actual constructor()

  actual operator fun component1() = sender
  actual operator fun component2() = receiver

  @BrevityInternalApi
  fun initBinding(bridge: HostBridge, binding: StreamBinding<T>) {
    if (!sender.hasRep && !receiver.hasRep) {
      sender.rep = bridge.putImportedResource(sender)
      sender.hasRep = true
      sender.buffer = Buffer(sender, StreamEndId.StreamWrite)

      receiver.rep = bridge.putImportedResource(receiver)
      receiver.hasRep = true
      receiver.buffer = Buffer(receiver, StreamEndId.StreamRead)
    }

    this.binding = binding
    this.memoryAllocator = bridge.memoryAllocator
  }

  actual class Sender<T> internal constructor(
    private val pair: Stream<T>,
  ) : Waitable() {
    internal lateinit var buffer: Buffer<T>

    internal var continuation: CancellableContinuation<Unit>? = null

    actual val receiverClosed: Boolean
      get() = TODO()

    actual suspend fun sendSome(values: List<T>): Int {
      buffer.write(values)

      pair.binding.transferIfPossible(
        memoryAllocator = pair.memoryAllocator,
        sendBuffer = pair.sender.buffer,
        receiveBuffer = pair.receiver.buffer,
      )

      if (buffer.pos == 0) {
        suspendCancellableCoroutine { continuation ->
          buffer.continuation = continuation
        }
      }

      check(buffer.pos > 0)
      val result = buffer.pos
      buffer.queued = false
      buffer.values.clear()
      buffer.continuation = null
      return result
    }

    @BrevityInternalApi
    fun write(address: Int, count: Int): Int {
      buffer.setAddressRange(pair.binding, address, count)

      pair.binding.transferIfPossible(
        memoryAllocator = pair.memoryAllocator,
        sendBuffer = pair.sender.buffer,
        receiveBuffer = pair.receiver.buffer,
      )

      return buffer.takeResult()
    }

    @BrevityInternalApi
    fun cancel(): Int {
      TODO()
    }
  }

  actual class Receiver<T> internal constructor(
    internal val pair: Stream<T>,
  ) : Waitable() {
    internal lateinit var buffer: Buffer<T>

    internal var continuation: CancellableContinuation<T>? = null

    actual val senderClosed: Boolean
      get() = TODO()

    @BrevityInternalApi
    fun initBinding(bridge: HostBridge, binding: StreamBinding<T>) {
      pair.initBinding(bridge, binding)
    }

    actual suspend fun receive(count: Int): List<T> {
      buffer.read(count)

      pair.binding.transferIfPossible(
        memoryAllocator = pair.memoryAllocator,
        sendBuffer = pair.sender.buffer,
        receiveBuffer = pair.receiver.buffer,
      )

      if (buffer.pos == 0) {
        suspendCancellableCoroutine { continuation ->
          buffer.continuation = continuation
        }
      }

      check(buffer.pos > 0)
      buffer.queued = false
      val result = buffer.values.toList()
      buffer.values.clear()
      buffer.continuation = null
      return result
    }

    @BrevityInternalApi
    fun read(address: Int, count: Int): Int {
      buffer.setAddressRange(pair.binding, address, count)
      pair.binding.transferIfPossible(
        memoryAllocator = pair.memoryAllocator,
        sendBuffer = pair.sender.buffer,
        receiveBuffer = pair.receiver.buffer,
      )
      return buffer.takeResult()
    }

    @BrevityInternalApi
    fun cancel(): Int {
      TODO()
    }
  }
}
