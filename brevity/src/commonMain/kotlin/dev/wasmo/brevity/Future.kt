@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package dev.wasmo.brevity

expect class Future<T> {
  val sender: Sender<T>
  val receiver: Receiver<T>

  constructor()

  operator fun component1(): Sender<T>
  operator fun component2(): Receiver<T>

  class Sender<T> : Resource {
    internal constructor(future: Future<T>)

    suspend fun complete(value: T)
  }

  class Receiver<T> : Resource {
    internal constructor(future: Future<T>)

    suspend fun await(): T
  }
}
