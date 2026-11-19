@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:OptIn(BrevityInternalApi::class)

package dev.wasmo.brevity

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
