@file:OptIn(InternalCoroutinesApi::class)

package dev.wasmo.brevity

import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Delay
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * A Wasm task is an invocation of an async function.
 */
@BrevityInternalApi
class RootTask(
  val name: String,
) : WasmTask {
  /*
   * This uses the Wasm component model for async features, instead of the kotlinx-coroutines
   * dispatchers which uses WASIp1 APIs.
   */

  private val dispatcher = TaskDispatcher()
  private val subtaskContinuations = mutableMapOf<Int, Continuation<SubtaskState>>()
  private val streamContinuations = mutableMapOf<Int, Continuation<PackedCopyResult>>()

  override val scope: CoroutineScope =
    CoroutineScope(BrevityCoroutineExceptionHandler + dispatcher + CoroutineName(name) + this)
  private val dispatchQueue = ArrayDeque<Pair<CoroutineContext, Runnable>>()

  /**
   * Suspends until the launched subtask completes.
   *
   * @throws CancellationException
   */
  suspend fun awaitSubtaskComplete(packedSubtaskResult: PackedSubtaskResult) {
    var subtaskState = packedSubtaskResult.subtaskState
    while (true) {
      when (subtaskState) {
        SubtaskState.Returned -> return
        SubtaskState.CanceledBeforeStarted, SubtaskState.CanceledBeforeReturned ->
          throw CancellationException()

        SubtaskState.Starting, SubtaskState.Started -> {
          subtaskState = suspendCancellableCoroutine { continuation ->
            subtaskContinuations[packedSubtaskResult.subtaskIndex] = continuation
          }
        }
      }
    }
  }

  /**
   * Suspends until at least one element is copied.
   *
   * @throws CancellationException
   */
  suspend fun awaitStreamProgress(streamIndex: Int, packedCopyResult: PackedCopyResult): Int {
    var packedCopyResult = packedCopyResult
    while (true) {
      when {
        packedCopyResult == PackedCopyResult.Blocked -> {
          packedCopyResult = suspendCancellableCoroutine { continuation ->
            streamContinuations[streamIndex] = continuation
          }
          continue
        }

        packedCopyResult.copyResult == CopyResult.Completed -> {
          return packedCopyResult.progress
        }

        packedCopyResult.copyResult == CopyResult.Dropped -> {
          throw CancellationException() // TODO: return a sentinel like '0' for dropped
        }

        packedCopyResult.copyResult == CopyResult.Cancelled -> {
          throw CancellationException()
        }
      }
    }
  }

  fun resume(event: Event): PackedAsyncResult {
    when (event) {
      Event.None -> TODO()
      is Event.Subtask -> {
        val continuation = subtaskContinuations.remove(event.subtaskIndex)
          ?: error("unexpected subtask: ${event.subtaskIndex}")
        continuation.resume(event.subtaskState)
      }

      is Event.Stream -> {
        val continuation = streamContinuations.remove(event.index)
          ?: error("unexpected stream: ${event.index}")
        continuation.resume(event.result)
      }

      Event.TaskCancelled -> TODO()
    }

    return runUntilIdle()
  }

  /** Execute all immediately-executable work, and return the new state of the suspended task. */
  fun runUntilIdle(): PackedAsyncResult {
    while (true) {
      val (context, runnable) = dispatchQueue.removeFirstOrNull() ?: break
      try {
        runnable.run()
      } catch (e: Throwable) {
        // TODO: do dispatched runnables call the exception handler automatically?
        val exceptionHandler = context[CoroutineExceptionHandler] ?: throw e
        exceptionHandler.handleException(context, e)
      }
    }

    if (subtaskContinuations.isNotEmpty() || streamContinuations.isNotEmpty()) {
      val waitableSet = newWaitableSet()
      for (waitableIndex in subtaskContinuations.keys) {
        waitableJoin(waitableIndex, waitableSet)
      }
      for (waitableIndex in streamContinuations.keys) {
        waitableJoin(waitableIndex, waitableSet)
      }
      return PackedAsyncResult(CallbackCode.Wait, waitableSet)
    }

    return PackedAsyncResult(CallbackCode.Exit)
  }

  inner class TaskDispatcher : CoroutineDispatcher(), Delay {
    override fun dispatch(
      context: CoroutineContext,
      block: Runnable,
    ) {
      dispatchQueue += context to block
    }

    override fun scheduleResumeAfterDelay(
      timeMillis: Long,
      continuation: CancellableContinuation<Unit>,
    ) {
      scope.launch {
        var resumed = false
        try {
          corePlatform.delay(timeMillis.milliseconds)
          resumed = true
          continuation.resume(Unit)
        } catch (e: CancellationException) {
          if (!resumed) {
            continuation.resume(Unit)
          }
          throw e
        }
      }
    }
  }

  override fun toString() = name
}
