@file:OptIn(BrevityInternalApi::class, UnsafeWasmMemoryApi::class, ExperimentalWasmInterop::class)
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package dev.wasmo.brevity

import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.withScopedMemoryAllocator
import kotlinx.coroutines.CompletableDeferred

actual class Stream<T> {
  actual val sender = Sender(this)
  actual val receiver = Receiver(this)

  internal val deferredBinding = CompletableDeferred<StreamBinding<T>>()

  actual constructor()

  actual operator fun component1() = sender
  actual operator fun component2() = receiver

  @BrevityInternalApi
  fun initBinding(binding: StreamBinding<T>) {
    if (!sender.hasRep && !receiver.hasRep) {
      val streamIds = binding.new()

      val sendRep = (streamIds ushr 32).toInt()
      GuestBridge.putImportedResource(sendRep, sender)
      sender.rep = sendRep
      sender.hasRep = true

      val receiveRep = streamIds.toInt()
      GuestBridge.putImportedResource(receiveRep, receiver)
      receiver.rep = receiveRep
      receiver.hasRep = true
    }

    deferredBinding.complete(binding)
  }

  @OptIn(UnsafeWasmMemoryApi::class)
  actual class Sender<T> internal constructor(
    private val pair: Stream<T>,
  ) : Resource() {

    actual val receiverClosed: Boolean
      get() = TODO()

    actual suspend fun sendSome(values: List<T>): Int {
      TODO()
    }

    suspend fun send(value: T) {
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

  actual class Receiver<T> internal constructor(
    private val pair: Stream<T>,
  ) : Resource() {
    actual val senderClosed: Boolean
      get() = TODO()

    actual suspend fun receive(count: Int): List<T> {
      TODO()
    }

    suspend fun receive(): T {
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
}
