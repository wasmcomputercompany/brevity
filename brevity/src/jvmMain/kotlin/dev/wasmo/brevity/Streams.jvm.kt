@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package dev.wasmo.brevity

actual class FuturePair<T> actual constructor() {
  actual val send = SendFuture(this)
  actual val receive = ReceiveFuture(this)

  actual operator fun component1() = send
  actual operator fun component2() = receive

  @BrevityInternalApi
  abstract class Binding<T> {
    abstract val byteCount: Int
    abstract fun store(memoryAllocator: MemoryAllocator, address: Int, value: T)
    abstract fun load(address: Int): T
  }
}

actual class SendFuture<T> internal actual constructor(
  private val pair: FuturePair<T>,
) : Resource() {
  actual suspend fun complete(value: T) {
    TODO()
  }

  @BrevityInternalApi
  fun write(address: Int): Int {
    TODO()
  }

  @BrevityInternalApi
  fun cancel(): Int {
    TODO()
  }
}

actual class ReceiveFuture<T> internal actual constructor(
  private val pair: FuturePair<T>,
) : Resource() {
  @BrevityInternalApi
  val peer: SendFuture<T>
    get() = pair.send

  actual suspend fun await(): T {
    TODO()
  }

  @BrevityInternalApi
  fun initBinding(binding: FuturePair.Binding<T>) {
    TODO()
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

actual class StreamPair<T> {
  actual val send = SendStream(this)
  actual val receive = ReceiveStream(this)

  actual constructor()

  actual operator fun component1() = send
  actual operator fun component2() = receive

  @BrevityInternalApi
  abstract class Binding<T> {
    abstract val byteCount: Int
    abstract fun store(memoryAllocator: MemoryAllocator, address: Int, value: T)
    abstract fun load(address: Int): T
  }
}

actual class SendStream<T> internal actual constructor(
  private val pair: StreamPair<T>,
) : Resource() {
  actual suspend fun send(value: T) {
    TODO()
  }

  @BrevityInternalApi
  fun write(address: Int, length: Int): Int {
    TODO()
  }

  @BrevityInternalApi
  fun cancel(): Int {
    TODO()
  }
}

actual class ReceiveStream<T> internal actual constructor(
  private val pair: StreamPair<T>,
) : Resource() {
  @BrevityInternalApi
  val peer: SendStream<T>
    get() = pair.send

  actual suspend fun receive(): T {
    TODO()
  }

  @BrevityInternalApi
  fun initBinding(binding: StreamPair.Binding<T>) {
    TODO()
  }

  @BrevityInternalApi
  fun read(address: Int, length: Int): Int {
    TODO()
  }

  @BrevityInternalApi
  fun cancel(): Int {
    TODO()
  }
}
