@file:OptIn(BrevityInternalApi::class)

package dev.wasmo.brevity

import kotlinx.coroutines.launch

@BrevityInternalApi
object GuestBridge {
  private val repToImportedResource = mutableMapOf<Int, Resource>()
  private val repToExportedResource = mutableMapOf<Int, Resource>()
  private val tasks = mutableMapOf<Int, RootTask>()
  private var nextRep = 4_000_000
  private var nextTaskId = 3_000_000

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

  fun <T : Resource> putImportedResource(rep: Int, resource: T) {
    repToImportedResource[rep] = resource
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

  fun launchTask(
    name: String,
    block: suspend () -> Unit,
  ): PackedAsyncResult {
    val taskId = nextTaskId++
    val task = RootTask(
      name = "$name $taskId",
    )
    contextSet0_export(taskId)
    tasks[taskId] = task

    task.scope.launch(task) {
      block()
    }

    return task.runUntilIdle()
  }

  fun resumeTask(event: Event): PackedAsyncResult {
    val taskId = contextGet0_export()
    val resumedTask = tasks[taskId]
      ?: error("unexpected task: $taskId")
    val result = resumedTask.resume(event)
    if (result.callbackCode == CallbackCode.Exit) {
      tasks.remove(taskId)
    }
    return result
  }
}
