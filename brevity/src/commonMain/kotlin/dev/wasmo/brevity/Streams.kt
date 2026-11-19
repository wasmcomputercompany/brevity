@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package dev.wasmo.brevity

expect class FuturePair<T> {
  val send: SendFuture<T>
  val receive: ReceiveFuture<T>

  constructor()

  operator fun component1(): SendFuture<T>
  operator fun component2(): ReceiveFuture<T>
}

expect class SendFuture<T> : Resource {
  internal constructor(pair: FuturePair<T>)

  suspend fun complete(value: T)
}

expect class ReceiveFuture<T> : Resource {
  internal constructor(pair: FuturePair<T>)

  suspend fun await(): T
}

expect class StreamPair<T> {
  constructor()

  val send: SendStream<T>
  val receive: ReceiveStream<T>

  operator fun component1(): SendStream<T>
  operator fun component2(): ReceiveStream<T>
}

expect class SendStream<T> : Resource {
  internal constructor(pair: StreamPair<T>)

  suspend fun send(value: T)
}

expect class ReceiveStream<T> : Resource {
  internal constructor(pair: StreamPair<T>)

  suspend fun receive(): T
}
