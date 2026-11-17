@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:OptIn(UnsafeWasmMemoryApi::class, BrevityInternalApi::class)

package dev.wasmo.brevity

import kotlin.wasm.unsafe.MemoryAllocator
import kotlin.wasm.unsafe.Pointer
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.withScopedMemoryAllocator
import kotlinx.coroutines.CompletableDeferred


actual class FuturePair<T> actual constructor() {
  actual val send = SendFuture(this)
  actual val receive = ReceiveFuture(this)

  actual operator fun component1() = send
  actual operator fun component2() = receive
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
  fun initBinding(binding: StreamBinding<T>) {
    TODO()
  }
}

actual class StreamPair<T> {
  actual val send = SendStream(this)
  actual val receive = ReceiveStream(this)

  internal val deferredBinding = CompletableDeferred<StreamBinding<T>>()

  actual constructor()

  actual operator fun component1() = send
  actual operator fun component2() = receive

  @BrevityInternalApi
  fun initBinding(binding: StreamBinding<T>) {
    if (!send.hasRep && !receive.hasRep) {
      val streamIds = binding.new()

      val sendRep = (streamIds ushr 32).toInt()
      GuestBridge.putImportedResource(sendRep, send)
      send.rep = sendRep
      send.hasRep = true

      val receiveRep = streamIds.toInt()
      GuestBridge.putImportedResource(receiveRep, receive)
      receive.rep = receiveRep
      receive.hasRep = true
    }

    deferredBinding.complete(binding)
  }
}

@OptIn(UnsafeWasmMemoryApi::class)
actual class SendStream<T> internal actual constructor(
  private val pair: StreamPair<T>,
) : Resource() {
  actual suspend fun send(value: T) {
    val binding = pair.deferredBinding.await()
    withScopedMemoryAllocator { memoryAllocator ->
      val address = memoryAllocator.allocate(binding.byteCount)
      binding.store(memoryAllocator, address, value)
      val writeResult = binding.write(rep, address.address.toInt(), 1).toUInt()
      val task = currentTask() as RootTask
      val progress = task.awaitStreamProgress(rep, PackedCopyResult(writeResult))
      check(progress == 1) { "unexpected progress: $progress" }
    }
  }
}

actual class ReceiveStream<T> internal actual constructor(
  private val pair: StreamPair<T>,
) : Resource() {
  actual suspend fun receive(): T {
    val binding = pair.deferredBinding.await()
    withScopedMemoryAllocator { memoryAllocator ->
      val address = memoryAllocator.allocate(binding.byteCount)
      val readResult = binding.read(rep, address.address.toInt(), 1).toUInt()
      val task = currentTask() as RootTask
      val progress = task.awaitStreamProgress(rep, PackedCopyResult(readResult))
      check(progress == 1) { "unexpected progress: $progress" }
      return binding.load(address)
    }
  }

  @BrevityInternalApi
  fun initBinding(binding: StreamBinding<T>) {
    pair.initBinding(binding)
  }
}

@BrevityInternalApi
abstract class StreamBinding<T> {
  abstract val byteCount: Int
  abstract fun new(): Long
  open fun read(index: Int, address: Int, count: Int): Int = error("streams only")
  open fun read(index: Int, address: Int): Int = error("futures only")
  open fun write(index: Int, address: Int, count: Int): Int = error("streams only")
  open fun write(index: Int, address: Int): Int = error("futures only")
  abstract fun cancelRead(index: Int): Int
  abstract fun cancelWrite(index: Int): Int
  abstract fun dropRead(index: Int)
  abstract fun dropWrite(index: Int)
  abstract fun store(memoryAllocator: MemoryAllocator, address: Pointer, value: T)
  abstract fun load(address: Pointer): T
}
