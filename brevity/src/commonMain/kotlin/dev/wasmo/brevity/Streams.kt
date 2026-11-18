@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package dev.wasmo.brevity

import kotlinx.coroutines.channels.ReceiveChannel

expect class FuturePair<T> {
  val send: SendFuture<T>
  val receive: ReceiveFuture<T>

  constructor()

  operator fun component1(): SendFuture<T>
  operator fun component2(): ReceiveFuture<T>
}

expect class SendFuture<T> : Resource {
  suspend fun complete(value: T)
}

expect class ReceiveFuture<T> : Resource {
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
  val isClosedForReceive: Boolean

  fun cancel()
  suspend fun send(value: T)
}

/**
 * Models the reading end of the WIT `stream<T>` type.
 *
 * When a `stream` is a parameter or return type in a WIT function declaration, it is always this
 * reading end that is passed. There is no mechanism to pass the writing end of a stream
 * ([SendStream]).
 */
expect class ReceiveStream<T> : Resource {
  /**
   * Returns true if an attempt to call [receive] will not succeed because the send stream is
   * closed.
   *
   * Note that the converse is not true: if this returns false a call to [receive] could still fail
   * because the send stream could be racily closed between the check and the action.
   */
  val isClosedForSend: Boolean

  /**
   * Receive up to [count] elements and return them as a list.
   *
   * This will suspend until:
   *
   *  * At least one element is received, or
   *  * The stream is canceled, or
   *  * The send stream is closed.
   *
   * Returns an empty list if the [SendStream] is closed or if this receive call is asynchronously
   * canceled.
   *
   * @param count the maximum number of elements to return. This may be zero, in which case the call
   *   will suspend until the sender calls [SendStream.send] with a non-empty list. Note that when
   *   using this mechanism, a subsequent receive may still suspend if the sender is racily
   *   canceled.
   * @throws IllegalStateException if this [ReceiveStream] is closed.
   * @throws IllegalArgumentException if count is negative.
   */
  suspend fun receive(count: Int = 1): List<T>

  /**
   * Cancels a currently-suspended call to [receive] or [receiveOrNull].
   *
   * Unlike [ReceiveChannel.cancel], this cancels only a single call, not the full stream. It is
   * supported and valid to receive on a stream after an earlier receive call was canceled. There is
   * no built-in mechanism to permanently cancel a stream.
   *
   * This does nothing if there is no currently-suspended receive call on this stream.
   */
  fun cancel()

  /**
   * Drops this stream. This sets [SendStream.isClosedForReceive] to true.
   *
   * After a stream is closed, it is an error to call [receive].
   *
   * @throws IllegalStateException if there is a currently-suspended call [receive] on this stream.
   */
  override fun close()
}

/**
 * Receive a single element and return it.
 *
 * This will suspend until:
 *
 *  * At least one element is received, or
 *  * The stream is canceled, or
 *  * The send stream is closed.
 *
 * Returns null if the [SendStream] is closed or if this receive call is asynchronously canceled.
 *
 * @throws IllegalStateException if this [ReceiveStream] is closed.
 */
expect suspend fun <T : Any> ReceiveStream<T>.receiveOrNull(): T?

/**
 * Receive up to [byteCount] bytes and write them to [sink] starting at [offset]. Returns the number
 * of bytes transmitted.
 *
 * This will suspend until:
 *
 *  * At least one byte is received, or
 *  * The stream is canceled, or
 *  * The send stream is closed.
 *
 * This returns 0 if the [SendStream] is closed or if this receive call is asynchronously canceled.
 *
 * @param byteCount the maximum number of elements to return. This may be zero, in which case the
 *   call will suspend until the sender calls [SendStream.send] with a non-empty list. Note that
 *   when using this mechanism, a subsequent receive may still suspend if the sender is racily
 *   canceled.
 * @throws IllegalStateException if this [ReceiveStream] is closed.
 */
expect suspend fun ReceiveStream<Byte>.receive(
  sink: ByteArray,
  offset: Int = 0,
  byteCount: Int = sink.size,
): Int

/**
 * Receive up to [count] unit values, Returns the number of unit values received.
 *
 * This will suspend until:
 *
 *  * At least one unit value is received, or
 *  * The stream is canceled, or
 *  * The send stream is closed.
 *
 * Returns 0 if the [SendStream] is closed or if this receive call is asynchronously canceled.
 *
 * Unlike all other stream types, `Stream<Unit>` does not copy values and so transmitting up to
 * `2 ^ 28 - 1` unit values is efficient.
 *
 * @param count the maximum number of values to receive. This may be zero, in which case the call
 *   will suspend until the sender calls [SendStream.send] with a non-zero count. Note that when
 *   using this mechanism, a subsequent receive may still suspend if the sender is racily
 *   canceled.
 * @throws IllegalStateException if this [ReceiveStream] is closed.
 * @throws IllegalArgumentException if count is negative.
 */
expect suspend fun ReceiveStream<Unit>.receiveUnits(count: Int = 1): Int
