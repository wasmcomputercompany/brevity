@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:OptIn(BrevityInternalApi::class)

package dev.wasmo.brevity

actual class Stream<T> {
  actual val sender = Sender(this)
  actual val receiver = Receiver(this)

  actual constructor()

  init {
    this.sender.end.other = receiver.end
    this.receiver.end.other = sender.end
  }

  actual operator fun component1() = sender
  actual operator fun component2() = receiver

  actual class Sender<T> internal constructor(
    private val pair: Stream<T>,
  ) : Waitable() {
    internal val end = StreamEnd<T>(this, StreamEndId.StreamWrite)

    actual val receiverClosed: Boolean
      get() = pair.receiver.end.closed

    actual suspend fun awaitReceive() {
      end.hostTransfer(
        data = StreamEnd.ListSource(listOf()),
        count = 0,
        rendezvous = true,
      )
    }

    actual suspend fun sendSome(values: List<T>): Int {
      return end.hostTransfer(
        data = StreamEnd.ListSource(values),
        count = values.size,
      )
    }

    @BrevityInternalApi
    fun write(address: Int, count: Int): Int {
      return end.guestTransfer(
        address = address,
        count = count,
      )
    }

    @BrevityInternalApi
    fun cancel(): Int {
      TODO()
    }

    actual override fun close() {
      end.close()
      super.close()
    }
  }

  actual class Receiver<T> internal constructor(
    internal val pair: Stream<T>,
  ) : Waitable() {
    internal val end = StreamEnd<T>(this, StreamEndId.StreamRead)

    actual val senderClosed: Boolean
      get() = pair.sender.end.closed

    @BrevityInternalApi
    fun initBinding(bridge: HostBridge, binding: StreamBinding<T>) {
      val sender = pair.sender
      val receiver = this

      if (!sender.hasRep && !receiver.hasRep) {
        sender.rep = bridge.putImportedResource(sender)
        sender.hasRep = true

        receiver.rep = bridge.putImportedResource(receiver)
        receiver.hasRep = true
      }

      sender.end.binding = binding
      sender.end.memoryAllocator = bridge.memoryAllocator
      receiver.end.binding = binding
      receiver.end.memoryAllocator = bridge.memoryAllocator
    }

    actual suspend fun awaitSend() {
      end.hostTransfer(
        data = StreamEnd.ListSink(mutableListOf()),
        count = 0,
        rendezvous = true,
      )
    }

    actual suspend fun receive(count: Int): List<T> {
      val result = mutableListOf<T>()

      end.hostTransfer(
        data = StreamEnd.ListSink(result),
        count = count,
      )

      return result
    }

    @BrevityInternalApi
    fun read(address: Int, count: Int): Int {
      return end.guestTransfer(
        address = address,
        count = count,
      )
    }

    @BrevityInternalApi
    fun cancel(): Int {
      TODO()
    }

    actual override fun close() {
      end.close()
      super.close()
    }
  }
}
