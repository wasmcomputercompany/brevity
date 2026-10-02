@file:OptIn(InternalCoroutinesApi::class)

package dev.wasmo.brevity

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Delay
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch

/**
 * This coroutines dispatcher uses the Wasm component model for async features, instead of the
 * kotlinx-coroutines dispatchers which uses WASIp1 APIs.
 */
@BrevityInternalApi
class BrevityDispatcher : CoroutineDispatcher(), Delay {
  val scope: CoroutineScope = CoroutineScope(this)
  private val queue = ArrayDeque<Pair<CoroutineContext, Runnable>>()

  private var timeMachine: TimeMachine? = null

  fun offerTimeMachine(timeMachine: TimeMachine) {
    val current = this.timeMachine
    if (current == null || current.wasiVersion >= timeMachine.wasiVersion) return
    this.timeMachine = timeMachine
  }

  fun runUntilIdle() {
    while (true) {
      val (context, runnable) = queue.removeFirstOrNull() ?: break
      runnable.run()
    }
  }

  override fun dispatch(
    context: CoroutineContext,
    block: Runnable,
  ) {
    queue += context to block
  }

  override fun scheduleResumeAfterDelay(
    timeMillis: Long,
    continuation: CancellableContinuation<Unit>,
  ) {
    scope.launch {
      var resumed = false
      try {
        timeMachine?.delay(timeMillis.milliseconds)
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

  interface TimeMachine {
    val wasiVersion: Int
    suspend fun delay(duration: Duration)
  }
}
