@file:OptIn(BrevityInternalApi::class, UnsafeWasmMemoryApi::class, ExperimentalWasmInterop::class)
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package dev.wasmo.brevity

import kotlin.coroutines.cancellation.CancellationException
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
    private var _receiverClosed = false
    actual val receiverClosed: Boolean
      get() = _receiverClosed

    actual suspend fun awaitReceive() {
      TODO()
    }

    actual suspend fun sendSome(values: List<T>): Int {
      val binding = pair.deferredBinding.await()
      val count = values.size
      val byteCount = binding.byteCount
      withScopedMemoryAllocator { memoryAllocator ->
        val address = memoryAllocator.allocate(count * byteCount)
        for ((i, element) in values.withIndex()) {
          binding.store(memoryAllocator, address + i * byteCount, element)
        }

        var writeResult = PackedCopyResult(
          binding.write(rep, address.address.toInt(), count).toUInt(),
        )

        if (writeResult == PackedCopyResult.Blocked) {
          val task = currentTask() as RootTask
          writeResult = task.awaitStreamProgress(rep)
        }

        return when (writeResult.copyResult) {
          CopyResult.Completed -> writeResult.progress
          CopyResult.Dropped -> {
            _receiverClosed = true
            0
          }
          CopyResult.Cancelled -> throw CancellationException()
        }
      }
    }

    actual override fun close() {
      // TODO: do something smart if the binding isn't initialized.
      try {
        val binding = pair.deferredBinding.getCompleted()
        binding.dropWrite(rep)
      } catch (_: IllegalStateException) {
      }
      super.close()
    }
  }

  actual class Receiver<T> internal constructor(
    private val pair: Stream<T>,
  ) : Resource() {
    private var _senderClosed = false
    actual val senderClosed: Boolean
      get() = _senderClosed

    @BrevityInternalApi
    fun initBinding(binding: StreamBinding<T>) {
      pair.initBinding(binding)
    }

    actual suspend fun awaitSend() {
      TODO()
    }

    actual suspend fun receive(count: Int): List<T> {
      val binding = pair.deferredBinding.await()
      val byteCount = binding.byteCount
      withScopedMemoryAllocator { memoryAllocator ->
        val address = memoryAllocator.allocate(count * byteCount)

        var readResult = PackedCopyResult(
          binding.read(rep, address.address.toInt(), count).toUInt(),
        )

        if (readResult == PackedCopyResult.Blocked) {
          val task = currentTask() as RootTask
          readResult = task.awaitStreamProgress(rep)
        }

        return when (readResult.copyResult) {
          CopyResult.Completed -> List(readResult.progress) {
            binding.load(address + byteCount * it)
          }
          CopyResult.Dropped -> {
            _senderClosed = true
            listOf()
          }
          CopyResult.Cancelled -> throw CancellationException()
        }
      }
    }

    actual override fun close() {
      // TODO: do something smart if the binding isn't initialized.
      try {
        val binding = pair.deferredBinding.getCompleted()
        binding.dropRead(rep)
      } catch (_: IllegalStateException) {
      }
      super.close()
    }
  }
}
