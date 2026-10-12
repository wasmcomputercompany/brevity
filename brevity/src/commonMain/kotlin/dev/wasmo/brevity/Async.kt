package dev.wasmo.brevity

import kotlin.jvm.JvmInline

@BrevityInternalApi
interface WaitableSet : Resource {
  /**
   * https://github.com/WebAssembly/component-model/blob/main/design/mvp/Explainer.md#-waitablejoin
   */
  fun join(waitableIndex: Int)

  /**
   * https://github.com/WebAssembly/component-model/blob/main/design/mvp/Explainer.md#-waitable-setpoll
   */
  fun poll(eventPointer: Int): Int
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
    fun <T> contextGet(index: Int, typeId: TypeId<T>): T

    /**
     * https://github.com/WebAssembly/component-model/blob/main/design/mvp/Explainer.md#-contextset
     */
    fun <T> contextSet(index: Int, typeId: TypeId<T>, value: T)

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
@JvmInline
value class TypeId<T> private constructor(
  val value: Int,
) {
  companion object {
    val I32 = TypeId<Int>(0)
  }
}

@BrevityInternalApi
enum class CallbackCode {
  Exit, Yield, Wait, Max;
}

@BrevityInternalApi
enum class EventCode {
  None,
  Subtask,
  StreamRead,
  StreamWrite,
  FutureRead,
  FutureWrite,
  TaskCancelled,
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
