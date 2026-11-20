@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

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
  }

  actual class Receiver<T> : Resource {
    internal actual constructor(future: Future<T>)

    @BrevityInternalApi
    fun initBinding(streamBinding: StreamBinding<T>) {
    }

    actual suspend fun await(): T {
      TODO()
    }
  }
}
