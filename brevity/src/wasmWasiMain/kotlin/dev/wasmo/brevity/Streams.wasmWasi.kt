@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:OptIn(BrevityInternalApi::class)

package dev.wasmo.brevity

import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.withScopedMemoryAllocator

actual class FuturePair<T> actual constructor() {
  actual val send = SendFuture(this)
  actual val receive = ReceiveFuture(this)

  actual operator fun component1() = send
  actual operator fun component2() = receive

  @BrevityInternalApi
  abstract class Binding<T> {
    abstract val byteCount: Int
    abstract fun new(): Long
    abstract fun read(index: Int, address: Int): Int
    abstract fun write(index: Int, address: Int): Int
    abstract fun cancelRead(index: Int): Int
    abstract fun cancelWrite(index: Int): Int
    abstract fun dropRead(index: Int)
    abstract fun dropWrite(index: Int)
    abstract fun lower(address: Int, value: T)
    abstract fun lift(address: Int): T
  }
}

actual class SendFuture<T> internal actual constructor(
  private val pair: FuturePair<T>,
) : Resource() {
  actual suspend fun complete(value: T) {
    TODO()
  }
}

actual class ReceiveFuture<T> internal actual constructor(
  private val pair: FuturePair<T>,
) : Resource() {
  actual suspend fun await(): T {
    TODO()
  }

  @BrevityInternalApi
  fun initBinding(binding: FuturePair.Binding<T>) {
    TODO()
  }
}

actual class StreamPair<T> {
  actual val send = SendStream(this)
  actual val receive = ReceiveStream(this)

  internal var binding: Binding<T>? = null

  actual constructor()

  actual operator fun component1() = send
  actual operator fun component2() = receive

  @BrevityInternalApi
  abstract class Binding<T> {
    abstract val byteCount: Int
    abstract fun new(): Long
    abstract fun read(index: Int, address: Int, length: Int): Int
    abstract fun write(index: Int, address: Int, length: Int): Int
    abstract fun cancelRead(index: Int): Int
    abstract fun cancelWrite(index: Int): Int
    abstract fun dropRead(index: Int)
    abstract fun dropWrite(index: Int)
    abstract fun lower(address: Int, value: T)
    abstract fun lift(address: Int): T
  }
}

@OptIn(UnsafeWasmMemoryApi::class)
actual class SendStream<T> internal actual constructor(
  private val pair: StreamPair<T>,
) : Resource() {
  actual suspend fun send(value: T) {
    // TODO: suspend until binding is set
    val binding = pair.binding ?: error("cannot send yet")

    withScopedMemoryAllocator { memoryAllocator ->
      val byteCount = binding.byteCount
      val address = memoryAllocator.allocate(byteCount)
      binding.lower(address.address.toInt(), value)
      val packedAsyncResult = binding.write(rep, address.address.toInt(), byteCount)
      // TODO: GuestBridge.awaitStreamWriteComplete.
    }
  }
}

actual class ReceiveStream<T> internal actual constructor(
  private val pair: StreamPair<T>,
) : Resource() {
  actual suspend fun receive(): T {
    TODO()
  }

  @BrevityInternalApi
  fun initBinding(binding: StreamPair.Binding<T>) {
    if (pair.binding == null) return

    val streamIds = binding.new()
    pair.send.rep = (streamIds ushr 32).toInt()
    pair.receive.rep = streamIds.toInt()
    GuestBridge.putImportedResource(pair.send.rep, pair.send)
    GuestBridge.putImportedResource(pair.receive.rep, pair.receive)
  }
}
