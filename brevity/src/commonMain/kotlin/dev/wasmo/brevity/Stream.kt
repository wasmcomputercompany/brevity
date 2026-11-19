@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package dev.wasmo.brevity

import kotlinx.coroutines.channels.Channel

/**
 * A WIT `stream<T>`.
 *
 * ### Passing Streams
 *
 * When a `stream` is a parameter or return type in a WIT declaration, it is always the
 * [Receiver] that is passed. There is no mechanism to pass the [Sender] of a stream pair.
 *
 * ### Concurrency
 *
 * Calls to [send] must be strictly serialized: it is an error to call [send] from one coroutine
 * before a previous call to [send] has returned.
 *
 * The same is true for [receive]: it is an error to call [receive] from one coroutine before a
 * previous call to [receive] has returned.
 *
 * But [send] and [receive] calls may overlap each other. Typically, Wasm performs one operation and
 * the host performs the other.
 *
 * ### Bulk Bytes
 *
 * This API offers specialized overloads for `Stream<Byte>` that do bulk transfer into a
 * `ByteArray` instead of a `List<Byte>`. At any scale this is a useful performance benefit. But
 * it’s especially important as `stream<byte>` is how all data gets into and out of Wasm:
 *
 * * HTTP request and response bodies
 * * File contents
 * * stdin, stdout, stderr
 *
 * ### Zero-sized sends and receives
 *
 * It is valid and supported to attempt to send and receive 0 elements.
 *
 * A send call of 0 will suspend until the receiver calls [Receiver.receive] with any count. The
 * sender can then perform whatever work is necessary to produce a value, and then send it with
 * confidence that the value will be immediately received.
 *
 * A receive call of 0 will suspend until the sender sends a non-empty list. The receiver can then
 * prepare to receive a value, and receive it with confidence that a value will be immediately sent.
 *
 * This is a useful mechanism to defer work, but it is imperfect. Sends and receives can both be
 * canceled, so there's always a risk that a ready stream stops being ready.
 *
 * ### Unit Streams
 *
 * Streams often include a type parameter, like `stream<u8>` or `stream<string>`. WIT also supports
 * streams with no type parameter, like `stream`. Brevity maps this to a Kotlin `StreamPair<Unit>`.
 * This type has a meaningful count but no data.
 *
 * Unlike all other stream types, `Stream<Unit>` does not copy values: sending and receiving
 * performs the same work to transmit 1 value as 1,000,000 values. Transmitting more than
 * `2 ^ 28 - 1` values requires a loop.
 *
 * ### Resource Leaks
 *
 * A stream may be used to safely transmit ownership of resources that must be managed. All
 * functions on this API precisely confirm how many values were successfully transmitted from sender
 * to receiver. Any unsent values remain the responsibility of the sender to release.
 *
 * ### Comparison with Channels
 *
 * This API is similar in use and implementation to coroutines' [Channel], but there are some
 * notable differences.
 *
 * Streams have simpler concurrency. The Wasm host and guest share a JVM thread. Data exchange is
 * always between different coroutines on the same thread. Our implementation doesn't involve
 * multicore concurrency or atomics. Stream sends and receives must each be strictly serialized.
 *
 * Streams can efficiently implement both high-level data types, like `stream<sql-row>`, and
 * low-level data types, like `stream<u8>`. This makes the API more complicated than channels,
 * which don't offer bulk operations.
 *
 * Streams are always unbuffered. This is similar to [Channel.Factory.RENDEZVOUS].
 *
 * Stream's ends are closed independently. It is necessary to close both the sender and the
 * receiver.
 */
expect class Stream<T> {
  constructor()

  val sender: Sender<T>
  val receiver: Receiver<T>

  operator fun component1(): Sender<T>
  operator fun component2(): Receiver<T>

  /**
   * The writing end of a stream.
   */
  class Sender<T> : Resource {
    /**
     * Returns true if any attempt to [send] will fail because the receive stream is closed.
     *
     * Note that the converse is not true: if this returns false a call to [send] could still fail
     * because the receive stream could be racily closed between the check and the action.
     */
    val receiverClosed: Boolean

    /**
     * Attempt to send at least one element of [values], and return the total number sent.
     *
     * This suspends until at least one element is sent.
     *
     * If the send stream is closed, this immediately sets [receiverClosed] to true and returns 0.
     *
     * @param values the elements to send. This may be empty, in which case the call will suspend
     *   until the receiver calls [Receiver.receive].
     * @throws IllegalStateException if this [Sender] is closed.
     */
    suspend fun sendSome(values: List<T>): Int

    /**
     * Drops this stream.
     *
     * After a stream is closed, it is an error to call [send].
     *
     * @throws IllegalStateException if there is a currently-suspended send on this stream.
     */
    override fun close()
  }

  /**
   * The reading end of a stream.
   */
  class Receiver<T> : Resource {
    /**
     * Returns true if an attempt to [receive] will fail because the send stream is closed.
     *
     * Note that the converse is not true: if this returns false a call to [receive] could still fail
     * because the send stream could be racily closed between the check and the action.
     */
    val senderClosed: Boolean

    /**
     * Receive up to [count] elements and return them as a list.
     *
     * This suspends until at least one element is received.
     *
     * If the send stream is closed, this immediately sets [senderClosed] to true and returns an
     * empty list.
     *
     * @param count the maximum number of elements to receive. This may be zero, in which case the
     *   call will suspend until the sender calls [Sender.send] with a non-empty list.
     * @throws IllegalStateException if this [Receiver] is closed.
     */
    suspend fun receive(count: Int = 1): List<T>

    /**
     * Drops this stream.
     *
     * After a stream is closed, it is an error to call [receive].
     *
     * @throws IllegalStateException if there is a currently-suspended receive on this stream.
     */
    override fun close()
  }
}

/**
 * Attempt to send all elements of [values] and return the size of the list.
 *
 * This suspends until all elements are sent.
 *
 * If the send stream is closed, this immediately sets [receiverClosed] to true and returns the
 * total number of elements successfully sent.
 *
 * @param values the elements to send. This may be empty, in which case the call will suspend
 *   until the receiver calls [Stream.Receiver.receive].
 * @throws IllegalStateException if this [Stream.Sender] is closed.
 */
suspend fun <T> Stream.Sender<T>.send(values: List<T>): Int {
  var pos = 0
  val limit = values.size
  while (pos < limit) {
    val sent = sendSome(values.subList(pos, limit))
    if (sent == 0) break
    pos += sent
  }
  return pos
}

/**
 * Attempt to send a value and return true.
 *
 * This suspends until the value is sent.
 *
 * If the send stream is closed, this immediately sets [receiverClosed] to true and returns
 * false.
 *
 * @throws IllegalStateException if this [Stream.Sender] is closed.
 */
suspend fun <T> Stream.Sender<T>.send(value: T): Boolean {
  return sendSome(listOf(value)) == 1
}

/**
 * Attempt to send up to [byteCount] bytes from [source] starting at [offset], and return the total
 * number of bytes sent.
 *
 * This suspends until at least one byte is sent.
 *
 * If the receive stream is closed, this immediately sets [receiverClosed] to true and returns
 * 0.
 *
 * @param byteCount the maximum number of bytes to send. This may be zero, in which case the call
 *   will suspend until the receiver calls [Stream.Receiver.receive].
 * @throws IllegalStateException if this [Stream.Sender] is closed.
 */
suspend fun Stream.Sender<Byte>.sendSome(
  source: ByteArray,
  offset: Int = 0,
  byteCount: Int = source.size,
): Int {
  return sendSome(List(byteCount) { source[offset + it] })
}

/**
 * Attempt to send up to [count] unit values.
 *
 * This suspends until at least one value is sent.
 *
 * This returns the number of unit values sent. If the receive stream is closed, this immediately
 * sets [receiverClosed] to true and returns 0.
 *
 * @param count the maximum number of values to send. This may be zero, in which case the call
 *   will suspend until the receiver calls [Stream.Receiver.receive] with a non-zero count.
 * @throws IllegalStateException if this [Stream.Sender] is closed.
 */
suspend fun Stream.Sender<Unit>.sendUnits(count: Int = 1): Int {
  return when (count) {
    0 -> sendSome(listOf())
    else -> sendSome(listOf(Unit))
  }
}

/**
 * Receive a single element and return it.
 *
 * This suspends until an element is received.
 *
 * If the send stream is closed, this immediately sets [senderClosed] to true and returns null.
 *
 * @throws IllegalStateException if this [Stream.Receiver] is closed.
 */
suspend fun <T : Any> Stream.Receiver<T>.receiveOrNull(): T? {
  val values = receive(1)
  return values.firstOrNull()
}

/**
 * Receive up to [byteCount] bytes and write them to [sink] starting at [offset].
 *
 * This suspends until at least one byte is received.
 *
 * This returns the number of bytes received. If the send stream is closed, this immediately sets
 * [senderClosed] to true and returns 0.
 *
 * @param byteCount the maximum number of bytes to receive. This may be zero, in which case the
 *   call will suspend until the sender calls [Stream.Sender.send] with a non-empty list.
 * @throws IllegalStateException if this [Stream.Receiver] is closed.
 */
suspend fun Stream.Receiver<Byte>.receive(
  sink: ByteArray,
  offset: Int = 0,
  byteCount: Int = sink.size,
): Int {
  val values = receive(byteCount)
  for (i in 0 until values.size) {
    sink[offset + i] = values[i]
  }
  return values.size
}

/**
 * Receive up to [count] unit values.
 *
 * This suspends until at least one value is received.
 *
 * This returns the number of unit values received. If the send stream is closed, this immediately
 * sets [senderClosed] to true and returns 0.
 *
 * @param count the maximum number of values to receive. This may be zero, in which case the call
 *   will suspend until the sender calls [Stream.Sender.send] with a non-zero count.
 * @throws IllegalStateException if this [Stream.Receiver] is closed.
 */
suspend fun Stream.Receiver<Unit>.receiveUnits(count: Int = 1): Int {
  val values = receive(count)
  return values.size
}
