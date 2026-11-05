@file:OptIn(UnsafeWasmMemoryApi::class)

package dev.wasmo.brevity

import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.withScopedMemoryAllocator

@OptIn(BrevityInternalApi::class)
actual fun <T, AT> StreamPair(): StreamPair<T, AT> = GuestStreamPair()

/**
 * The host side of a stream pair is where the interesting concurrency happens.
 */
@BrevityInternalApi
class GuestStreamPair<T, AT> : StreamPair<T, AT> {
  override val sendStream = GuestSendStream()
  override val receiveStream = GuestReceiveStream()

  private var binding: StreamBinding<T, AT>? = null

  fun initBinding(binding: StreamBinding<T, AT>) {
    if (this.binding != null) return

    this.binding = binding
    val indexes = binding.newStream()
    sendStream.rep = indexes.toInt()
    sendStream.hasRep = true
    receiveStream.rep = (indexes ushr 32).toInt()
    receiveStream.hasRep = true
  }

  inner class GuestSendStream : SendStream<T, AT>() {
    private var closed = false

    override suspend fun send(value: T) {
      check(!closed)
      val binding = binding ?: error("cannot send on this stream")
      return withScopedMemoryAllocator { memoryAllocator ->
        val address = memoryAllocator.allocate(binding.byteCount)
        val addressInt = address.address.toInt()
        binding.lower(addressInt, value)
        val writeResult = binding.write(rep, addressInt, 1) // TODO: byte count or length?
        // TODO: honor read result. Suspend?
      }
    }

    override suspend fun send(source: AT, offset: Int, count: Int) {
      TODO("Not yet implemented")
    }

    override fun cancel() {
      if (closed) return
      val binding = binding ?: error("cannot send on this stream")
      binding.cancelWrite(rep)
    }

    override fun close() {
      if (closed) return
      closed = true
      val binding = binding ?: error("cannot receive on this stream")
      binding.dropWrite(rep)
    }
  }

  inner class GuestReceiveStream : ReceiveStream<T, AT>() {
    private var closed = false

    override suspend fun receive(): T {
      check(!closed)
      val binding = binding ?: error("cannot receive on this stream")
      return withScopedMemoryAllocator { memoryAllocator ->
        val address = memoryAllocator.allocate(binding.byteCount)
        val addressInt = address.address.toInt()
        val readResult = binding.read(rep, addressInt, 1) // TODO: byte count or length?
        // TODO: honor read result. Suspend?
        binding.lift(addressInt)
      }
    }

    override suspend fun receive(
      destination: AT,
      offset: Int,
      count: Int,
    ): Int {
      TODO("Not yet implemented")
    }

    override fun cancel() {
      if (closed) return
      val binding = binding ?: error("cannot receive on this stream")
      binding.cancelRead(rep)
    }

    override fun close() {
      if (closed) return
      closed = true
      val binding = binding ?: error("cannot receive on this stream")
      binding.dropRead(rep)
    }
  }
}

@BrevityInternalApi
interface StreamBinding<T, AT> {
  val byteCount: Int
  fun newStream(): Long

  fun write(index: Int, address: Int, length: Int): Int
  fun lower(address: Int, value: T)
  fun lower(address: Int, source: AT, sourceOffset: Int, count: Int)
  fun cancelWrite(index: Int): Int
  fun dropWrite(index: Int)

  fun read(index: Int, address: Int, length: Int): Int
  fun lift(address: Int): T
  fun lift(address: Int, destination: AT, destinationOffset: Int, count: Int)
  fun cancelRead(index: Int): Int
  fun dropRead(index: Int)
}
