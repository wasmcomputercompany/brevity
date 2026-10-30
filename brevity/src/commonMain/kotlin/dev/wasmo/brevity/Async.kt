package dev.wasmo.brevity

import kotlin.jvm.JvmInline

/**
 * Note that a waitable can be in at most one set. Joining a set removes the waitable from its
 * current set, if any. Joining waitable 0 removes it from all sets.
 */
@BrevityInternalApi
abstract class WaitableSet : Resource() {
  /**
   * https://github.com/WebAssembly/component-model/blob/main/design/mvp/Explainer.md#-waitablejoin
   */
  abstract fun join(waitableIndex: Int)

  /**
   * https://github.com/WebAssembly/component-model/blob/main/design/mvp/Explainer.md#-waitable-setpoll
   */
  abstract fun poll(eventPointer: Int): Int
}

@BrevityInternalApi
object Async {
  interface Guest

  interface Host {
    /**
     * https://github.com/WebAssembly/component-model/blob/main/design/mvp/Explainer.md#-waitable-setnew
     */
    fun waitableSetNew(): WaitableSet

    /**
     * https://github.com/WebAssembly/component-model/blob/main/design/mvp/Explainer.md#-contextget
     */
    fun contextGet(index: Int): Int

    /**
     * https://github.com/WebAssembly/component-model/blob/main/design/mvp/Explainer.md#-contextset
     */
    fun contextSet(index: Int, value: Int)

    /**
     * https://github.com/WebAssembly/component-model/blob/main/design/mvp/Explainer.md#-taskcancel
     */
    fun taskCancel()

    /**
     * https://github.com/WebAssembly/component-model/blob/main/design/mvp/Explainer.md#-subtaskcancel
     */
    fun subtaskCancel(subtask: Int): Int

    /**
     * https://github.com/WebAssembly/component-model/blob/main/design/mvp/CanonicalABI.md#-canon-subtaskdrop
     */
    fun subtaskDrop(subtask: Int)
  }
}

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
    private val SubtaskStateValues = SubtaskState.entries.toTypedArray()
    private val MaxSubtaskIndex = 1 shl 28
  }
}

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

  object StreamRead : Event(
    code = 2,
  )

  object StreamWrite : Event(
    code = 3,
  )

  object FutureRead : Event(
    code = 4,
  )

  object FutureWrite : Event(
    code = 5,
  )

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
