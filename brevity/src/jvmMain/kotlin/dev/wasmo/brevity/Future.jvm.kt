@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:OptIn(BrevityInternalApi::class)

package dev.wasmo.brevity

actual class Future<T> actual constructor() {
  actual val sender = Sender(this)
  actual val receiver = Receiver(this)

  actual operator fun component1(): Sender<T> = sender
  actual operator fun component2(): Receiver<T> = receiver

  actual class Sender<T> : Resource {
    internal actual constructor(future: Future<T>)

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

  actual class Receiver<T> : Resource {
    internal actual constructor(future: Future<T>)

    actual suspend fun await(): T {
      TODO()
    }

    @BrevityInternalApi
    fun initBinding(bridge: HostBridge, stream: StreamBinding<T>) {
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
}

