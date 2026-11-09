@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:OptIn(BrevityInternalApi::class)

package dev.wasmo.brevity

import kotlinx.coroutines.selects.SelectBuilder

actual class FuturePair<T> actual constructor() {
  actual val send = SendFuture(this)
  actual val receive = ReceiveFuture(this)

  internal var binding: Binding<T>? = null

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
) : Resource(), Waitable {
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

  override fun poll(): Event {
    TODO("Not yet implemented")
  }

  override fun select(selectBuilder: SelectBuilder<Event>) {
    TODO("Not yet implemented")
  }
}

actual class ReceiveFuture<T> internal actual constructor(
  private val pair: FuturePair<T>,
) : Resource(), Waitable {
  @BrevityInternalApi
  val peer: SendFuture<T>
    get() = pair.send

  actual suspend fun await(): T {
    TODO()
  }

  @BrevityInternalApi
  fun initBinding(bridge: HostBridge, binding: FuturePair.Binding<T>) {
    check(!this.hasRep && !this.peer.hasRep && pair.binding == null)
    this.rep = bridge.putImportedResource(this)
    this.hasRep = true
    this.peer.rep = bridge.putImportedResource(this.peer)
    this.peer.hasRep = true
    pair.binding = binding
  }

  @BrevityInternalApi
  fun read(address: Int): Int {
    return PackedCopyResult.Blocked.value.toInt()
  }

  @BrevityInternalApi
  fun cancel(): Int {
    TODO()
  }

  override fun poll(): Event {
    TODO("Not yet implemented")
  }

  override fun select(selectBuilder: SelectBuilder<Event>) {
    TODO("Not yet implemented")
  }
}

actual class StreamPair<T> {
  actual val send = SendStream(this)
  actual val receive = ReceiveStream(this)

  internal var binding: Binding<T>? = null

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
) : Resource(), Waitable {
  actual suspend fun send(value: T) {
    TODO()
  }

  @BrevityInternalApi
  fun write(address: Int, count: Int): Int {
    return Event.StreamWrite(rep, PackedCopyResult.Blocked).p2
  }

  @BrevityInternalApi
  fun cancel(): Int {
    TODO()
  }

  override fun poll(): Event {
    return Event.StreamWrite(rep, PackedCopyResult.Blocked)
  }

  override fun select(selectBuilder: SelectBuilder<Event>) {
    TODO("Not yet implemented")
  }
}

actual class ReceiveStream<T> internal actual constructor(
  private val pair: StreamPair<T>,
) : Resource(), Waitable {
  @BrevityInternalApi
  val peer: SendStream<T>
    get() = pair.send

  actual suspend fun receive(): T {
    TODO()
  }

  @BrevityInternalApi
  fun initBinding(bridge: HostBridge, binding: StreamPair.Binding<T>) {
    check(!this.hasRep && !this.peer.hasRep && pair.binding == null)
    this.rep = bridge.putImportedResource(this)
    this.hasRep = true
    this.peer.rep = bridge.putImportedResource(this.peer)
    this.peer.hasRep = true
    pair.binding = binding
  }

  @BrevityInternalApi
  fun read(address: Int, count: Int): Int {
    return Event.StreamRead(rep, PackedCopyResult.Blocked).p2
  }

  @BrevityInternalApi
  fun cancel(): Int {
    TODO()
  }

  override fun poll(): Event {
    return Event.StreamRead(rep, PackedCopyResult.Blocked)
  }

  override fun select(selectBuilder: SelectBuilder<Event>) {
    TODO("Not yet implemented")
  }
}
