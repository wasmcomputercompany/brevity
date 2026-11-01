@file:OptIn(BrevityInternalApi::class)

package dev.wasmo.brevity

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch

@BrevityInternalApi
object GuestBridge {
  private val repToImportedResource = mutableMapOf<Int, Resource>()
  private val repToExportedResource = mutableMapOf<Int, Resource>()
  private var nextRep = 4_040_000

  // TODO: use context to identify which task is executing?
  private var theOnlyTask: BrevityTask? = null

  // Prevent kotlinx-coroutines DefaultExecutor from registering a hook when exported functions
  // exit. That hook is particularly problematic for `cabi_realloc`, because it prevents that
  // function from being bridged.
  init {
    kotlin.wasm.internal.onExportedFunctionExit = {}
  }

  fun <T : Resource> putExportedResource(resource: T): Int {
    val rep = nextRep++
    repToExportedResource[rep] = resource
    return rep
  }

  fun <T : Resource> getExportedResource(rep: Int): T {
    val resource = repToExportedResource[rep] ?: error("unexpected rep: $rep")
    return resource as T
  }

  fun <T : Resource> putImportedResource(resource: T): Int {
    val rep = nextRep++
    repToImportedResource[rep] = resource
    return rep
  }

  fun <T : Resource> getImportedResource(rep: Int, newHandle: () -> T): T {
    val existing = repToImportedResource[rep]
    if (existing == null) {
      val result = newHandle()
      result.rep = rep
      result.hasRep = true
      return result
    }
    return existing as T
  }

  fun contextGet(index: Int): Int {
    return when (index) {
      0 -> contextGet0_export()
      1 -> contextGet1_export()
      else -> error("unexpected index: $index")
    }
  }

  fun contextSet(index: Int, value: Int) {
    when (index) {
      0 -> contextSet0_export(value)
      1 -> contextSet1_export(value)
      else -> error("unexpected index: $index")
    }
  }

  fun launchTask(block: suspend () -> Unit): PackedAsyncResult {
    val task = BrevityTask()
    theOnlyTask = task
    task.scope.launch(task) {
      try {
        block()
      } finally {
        task.complete = true
        theOnlyTask = null
      }
    }

    return task.runUntilIdle()
  }

  /**
   * Suspends until the launched subtask completes.
   *
   * @throws CancellationException
   */
  suspend fun awaitSubtaskComplete(packedSubtaskResult: PackedSubtaskResult) {
    when (packedSubtaskResult.subtaskState) {
      SubtaskState.Starting, SubtaskState.Started -> {
        // We're about to suspend, so the clear active task.
        val task = currentCoroutineContext()[BrevityTask.Key]
          ?: error("cannot launch subtask without a task")
        task.awaitSubtaskComplete(packedSubtaskResult.subtaskIndex)
      }

      SubtaskState.Returned -> {
      }

      SubtaskState.CanceledBeforeStarted, SubtaskState.CanceledBeforeReturned ->
        throw CancellationException()
    }
  }

  fun resumeTask(event: Event): PackedAsyncResult {
    val resumedTask = theOnlyTask!! // TODO: obviously inadequate.
    return resumedTask.resume(event)
  }
}
