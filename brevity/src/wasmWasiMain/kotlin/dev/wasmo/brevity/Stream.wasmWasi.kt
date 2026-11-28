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
    private var closed = false
    private var _receiverClosed = false
    actual val receiverClosed: Boolean
      get() = _receiverClosed

    actual suspend fun awaitReceive() {
      write(
        count = 0,
        storer = Storer.ListStorer(listOf()),
        rendezvous = true,
      )
    }

    actual suspend fun sendSome(values: List<T>): Int {
      return write(
        count = values.size,
        storer = Storer.ListStorer(listOf()),
      )
    }

    internal suspend fun write(
      count: Int,
      storer: Storer<T>,
      rendezvous: Boolean = false,
    ): Int {
      check(!closed) { "closed" }
      val binding = pair.deferredBinding.await()

      if (count == 0 && !rendezvous) return 0

      val byteCount = binding.byteCount
      withScopedMemoryAllocator { memoryAllocator ->
        val address = memoryAllocator.allocate(count * byteCount)
        storer.store(binding, memoryAllocator, address)

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
      if (closed) return
      closed = true

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
    private var closed = false
    private var _senderClosed = false
    actual val senderClosed: Boolean
      get() = _senderClosed

    @BrevityInternalApi
    fun initBinding(binding: StreamBinding<T>) {
      pair.initBinding(binding)
    }

    actual suspend fun awaitSend() {
      read(
        count = 0,
        loader = Loader.ListLoader(),
        rendezvous = true,
      )
    }

    actual suspend fun receive(count: Int): List<T> {
      val loader = Loader.ListLoader<T>()
      read(
        count = count,
        loader = loader,
      )
      return loader.list
    }

    internal suspend fun read(
      count: Int,
      loader: Loader<T>,
      rendezvous: Boolean = false,
    ) : Int {
      check(!closed) { "closed" }
      val binding = pair.deferredBinding.await()

      if (count == 0 && !rendezvous) return 0

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
          CopyResult.Completed -> {
            loader.load(binding, address, readResult.progress)
            readResult.progress
          }

          CopyResult.Dropped -> {
            _senderClosed = true
            0
          }

          CopyResult.Cancelled -> throw CancellationException()
        }
      }
    }

    actual override fun close() {
      if (closed) return
      closed = true

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


actual suspend fun Stream.Receiver<Unit>.receiveUnits(count: Int): Int {
  return read(
    count = count,
    loader = Loader.UnitLoader,
  )
}

actual suspend fun Stream.Sender<Unit>.sendUnits(count: Int): Int {
  return write(
    count = count,
    storer = Storer.UnitStorer,
  )
}
