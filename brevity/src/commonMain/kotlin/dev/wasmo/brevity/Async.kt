package dev.wasmo.brevity

import kotlin.jvm.JvmInline

@BrevityInternalApi
enum class CallbackCode {
  Exit, Yield, Wait, Max;
}

/**
 * https://github.com/WebAssembly/component-model/blob/main/design/mvp/CanonicalABI.md#canon-lift
 */
@BrevityInternalApi
@JvmInline
value class PackedAsyncResult(val value: UInt) {
  init {
    require((value.toInt() and 0xf) < CallbackCodeValues.size)
  }

  val callbackCode: CallbackCode
    get() = CallbackCodeValues[value.toInt() and 0xf]

  val waitableSetIndex: Int
    get() = value.toInt() ushr 4

  constructor(callbackCode: CallbackCode, waitableSetIndex: Int = 0)
    : this((callbackCode.ordinal or (waitableSetIndex shl 4)).toUInt()) {
    require(waitableSetIndex < MaxWaitableSetIndex)
  }

  companion object {
    private val CallbackCodeValues = CallbackCode.entries.toTypedArray()
    private val MaxWaitableSetIndex = 1 shl 28
  }
}

@BrevityInternalApi
enum class SubtaskState {
  Starting,
  Started,
  Returned,
  CanceledBeforeStarted,
  CanceledBeforeReturned,
}

@BrevityInternalApi
private val SubtaskStateValues = SubtaskState.entries.toTypedArray()

/**
 * https://github.com/WebAssembly/component-model/blob/main/design/mvp/CanonicalABI.md#canon-lower
 */
@BrevityInternalApi
@JvmInline
value class PackedSubtaskResult(val value: UInt) {
  init {
    require((value.toInt() and 0xf) < SubtaskStateValues.size)
  }

  val subtaskState: SubtaskState
    get() = SubtaskStateValues[value.toInt() and 0xf]

  val subtaskIndex: Int
    get() = value.toInt() ushr 4

  constructor(subtaskState: SubtaskState, subtaskIndex: Int = 0)
    : this((subtaskState.ordinal or (subtaskIndex shl 4)).toUInt()) {
    require(subtaskIndex < MaxSubtaskIndex)
  }

  companion object {
    private val MaxSubtaskIndex = 1 shl 28
  }
}

@BrevityInternalApi
enum class CopyResult {
  /** Copy is done. */
  Completed, // 0

  /** When the _other_ end is dropped, and no more I/O is possible. */
  Dropped, // 1

  /** This end is canceled and the memory is no longer shared. */
  Cancelled, // 2
}

@BrevityInternalApi
private val CopyResultValues = CopyResult.entries.toTypedArray()

@BrevityInternalApi
@JvmInline
value class PackedCopyResult(val value: UInt) {
  init {
    require(value == 0xffffffffU || (value.toInt() and 0xf) < CopyResultValues.size)
  }

  val copyResult: CopyResult
    get() = CopyResultValues[value.toInt() and 0xf]

  val progress: Int
    get() = value.toInt() ushr 4

  constructor(copyResult: CopyResult, progress: Int = 0)
    : this((copyResult.ordinal or (progress shl 4)).toUInt()) {
    require(progress <= MAX_PROGRESS)
  }

  companion object {
    val Blocked = PackedCopyResult(0xffffffffU)

    /** The maximum number of elements that can be transferred in a single operation. */
    const val MAX_PROGRESS = (1 shl 28) - 1
  }
}

/**
 * Note that calling [take] returns the latest-known state of the waitable, which may be different
 * from the state it was in when the event was enqueued.
 */
@BrevityInternalApi
fun interface PendingEvent {
  fun take(): Event

  object None : PendingEvent {
    override fun take() = Event.None
  }
}

enum class StreamEndId {
  StreamRead,
  StreamWrite,
  FutureRead,
  FutureWrite,
}

@BrevityInternalApi
private val StreamEndIdValues = StreamEndId.entries.toTypedArray()

@BrevityInternalApi
sealed class Event(
  val code: Int,
  val p1: Int = 0,
  val p2: Int = 0,
) {
  object None : Event(
    code = 0,
  )

  class Subtask(
    subtaskIndex: Int,
    subtaskState: SubtaskState,
  ) : Event(
    code = 1,
    p1 = subtaskIndex,
    p2 = subtaskState.ordinal,
  ) {
    val subtaskIndex: Int
      get() = p1
    val subtaskState: SubtaskState
      get() = SubtaskState.entries[p2]
  }

  class Stream(streamEndId: StreamEndId, index: Int, result: PackedCopyResult) : Event(
    code = streamEndId.ordinal + 2,
    p1 = index,
    p2 = result.value.toInt(),
  ) {
    val end: StreamEndId
      get() = StreamEndIdValues[code - 2]
    val index: Int
      get() = p1
    val result: PackedCopyResult
      get() = PackedCopyResult(p2.toUInt())
  }

  object TaskCancelled : Event(
    code = 6,
  )

  companion object {
    operator fun invoke(code: Int, p1: Int, p2: Int): Event {
      return when (code) {
        0 -> None
        1 -> Subtask(p1, SubtaskState.entries[p2])
        else -> error("unexpected event code")
      }
    }
  }
}
