package dev.wasmo.brevity

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * Execute a host function.
 *
 * This executes functions using [CoroutineStart.UNDISPATCHED] so if the subtask doesn't suspend we
 * can return synchronously.
 */
@BrevityInternalApi
class Subtask(
  private val bridge: HostBridge,
  parent: RootTask<*>,
) : Waitable(), WasmTask {
  override val scope = CoroutineScope(parent.scope.coroutineContext + this)

  fun start(
    block: suspend () -> Unit,
  ): PackedSubtaskResult {
    scope.launch(start = CoroutineStart.UNDISPATCHED) {
      val subtaskState = try {
        block()
        SubtaskState.Returned
      } catch (_: CancellationException) {
        SubtaskState.CanceledBeforeReturned
      }

      enqueueEvent {
        Event.Subtask(rep, subtaskState)
      }
    }

    return when (val event = take()) {
      // If the subtask has already completed, we don't need to assign a rep.
      is Event.Subtask -> PackedSubtaskResult(event.subtaskState)

      // The subtask hasn't completed yet.
      Event.None -> {
        this.rep = bridge.putImportedResource(this)
        this.hasRep = true
        PackedSubtaskResult(SubtaskState.Started, rep)
      }

      else -> error("unexpected event")
    }
  }
}
