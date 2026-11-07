@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package dev.wasmo.brevity

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
  actual fun initBinding(binding: Binding<T>) {
    TODO()
  }

  @BrevityInternalApi
  actual abstract class Binding<T> {
    abstract fun new(): Long
    abstract fun read(index: Int, address: Int): Int
    abstract fun write(index: Int, address: Int): Int
    abstract fun cancelRead(index: Int): Int
    abstract fun cancelWrite(index: Int): Int
    abstract fun dropRead(index: Int)
    abstract fun dropWrite(index: Int)
    actual abstract fun lower(address: Int, value: T)
    actual abstract fun lift(address: Int): T
  }
}

actual class StreamPair<T> {
  actual val send = SendStream(this)
  actual val receive = ReceiveStream(this)

  actual constructor()

  actual operator fun component1() = send
  actual operator fun component2() = receive
}

actual class SendStream<T> internal actual constructor(
  private val pair: StreamPair<T>,
) : Resource() {
  actual suspend fun send(value: T) {
    TODO()
  }
}

actual class ReceiveStream<T> internal actual constructor(
  private val pair: StreamPair<T>,
) : Resource() {
  actual suspend fun receive(): T {
    TODO()
  }

  @BrevityInternalApi
  actual fun initBinding(binding: Binding<T>) {
    TODO()
  }

  @BrevityInternalApi
  actual abstract class Binding<T> {
    abstract fun new(): Long
    abstract fun read(index: Int, address: Int, length: Int): Int
    abstract fun write(index: Int, address: Int, length: Int): Int
    abstract fun cancelRead(index: Int): Int
    abstract fun cancelWrite(index: Int): Int
    abstract fun dropRead(index: Int)
    abstract fun dropWrite(index: Int)
    actual abstract fun lower(address: Int, value: T)
    actual abstract fun lift(address: Int): T
  }
}
