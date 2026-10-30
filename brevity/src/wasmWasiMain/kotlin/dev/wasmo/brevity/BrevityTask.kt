@file:OptIn(InternalCoroutinesApi::class)

package dev.wasmo.brevity

import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Delay
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * This dispatcher is scoped to a single task.
 *
 * This uses the Wasm component model for async features, instead of the kotlinx-coroutines
 * dispatchers which uses WASIp1 APIs.
 */
@BrevityInternalApi
internal class BrevityTask : CoroutineContext.Element {
  private val dispatcher = BrevityDispatcher()
  private val subtaskContinuations = mutableMapOf<Int, Continuation<Unit>>()
  var complete = false

  val scope = CoroutineScope(BrevityCoroutineExceptionHandler + dispatcher + this)
  private val dispatchQueue = ArrayDeque<Pair<CoroutineContext, Runnable>>()

  override val key: CoroutineContext.Key<*>
    get() = Key

  suspend fun awaitSubtaskComplete(subtaskIndex: Int) {
    suspendCancellableCoroutine { continuation ->
      subtaskContinuations[subtaskIndex] = continuation
    }
  }

  fun resume(event: Event): PackedAsyncResult {
    when (event) {
      Event.None -> TODO()
      is Event.Subtask -> {
        val continuation = subtaskContinuations.remove(event.subtaskIndex)
          ?: error("no such subtask: ${event.subtaskIndex}")
        when (event.subtaskState) {
          SubtaskState.Returned -> continuation.resume(Unit)
          SubtaskState.CanceledBeforeStarted, SubtaskState.CanceledBeforeReturned ->
            continuation.resumeWithException(CancellationException())

          SubtaskState.Starting -> {}
          SubtaskState.Started -> {}
        }
        return runUntilIdle()
      }

      Event.FutureRead -> TODO()
      Event.FutureWrite -> TODO()
      Event.StreamRead -> TODO()
      Event.StreamWrite -> TODO()
      Event.TaskCancelled -> TODO()
    }
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

    if (complete) return PackedAsyncResult(CallbackCode.Exit)

    val waitableSet = Async.host.waitableSetNew()
    for (subtaskIndex in subtaskContinuations.keys) {
      waitableSet.join(subtaskIndex)
    }
    return PackedAsyncResult(CallbackCode.Wait, waitableSet.rep)
  }

  @BrevityInternalApi
  inner class BrevityDispatcher : CoroutineDispatcher(), Delay {
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

  companion object Key : CoroutineContext.Key<BrevityTask>
}
