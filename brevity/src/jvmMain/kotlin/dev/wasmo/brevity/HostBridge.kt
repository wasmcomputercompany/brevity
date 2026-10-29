package dev.wasmo.brevity

import com.dylibso.chicory.runtime.ExportFunction
import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.runtime.Memory
import dev.wasmo.brevity.RealAsyncHost.RealWaitableSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

@BrevityInternalApi
class HostBridge(
  private val coroutineScope: CoroutineScope,
) {
  private val repToExportedResource = mutableMapOf<Int, Resource>()
  private val repToImportedResource = mutableMapOf<Int, Resource>()
  internal val waitables = mutableMapOf<Int, RealAsyncHost.Waitable>()
  private var nextRep = 6_060_000
  private var nextWaitableIndex = 8_080_000
  private lateinit var _memory: Memory

  val memory: Memory
    get() = _memory

  lateinit var memoryAllocator: MemoryAllocator
    private set

  private var taskResult: Any? = null

  fun init(instance: Instance) {
    this._memory = instance.memory()
    this.memoryAllocator = MemoryAllocator(
      cabiRealloc = instance.export("cabi_realloc"),
    )
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

  fun <T : Resource> getImportedResource(rep: Int): T {
    val resource = repToImportedResource[rep] ?: error("unexpected rep: $rep")
    return resource as T
  }

  @PublishedApi
  internal fun dropInternal(rep: Int): Resource? {
    return repToExportedResource.remove(rep)
  }

  suspend fun <T> awaitTaskComplete(
    packedAsyncResult: PackedAsyncResult,
    callback: (event: Event) -> PackedAsyncResult,
  ): T {
    var packedAsyncResult = packedAsyncResult

    while (true) {
      when (packedAsyncResult.callbackCode) {
        CallbackCode.Exit -> return taskResult as T

        CallbackCode.Yield -> {
          yield()
          packedAsyncResult = callback(Event.None)
        }

        CallbackCode.Wait -> {
          val waitableSet = getImportedResource<RealWaitableSet>(packedAsyncResult.waitableSetIndex)
          val event = waitableSet.waitForEvent()
          packedAsyncResult = callback(event)
        }

        CallbackCode.Max -> TODO()
      }
    }
  }

  fun launchSubtask(block: suspend () -> Unit): PackedSubtaskResult {
    val job = coroutineScope.launch {
      block()
    }

    return when {
      job.isCancelled -> PackedSubtaskResult(SubtaskState.CanceledBeforeReturned)

      !job.isCompleted -> {
        val waitableIndex = nextWaitableIndex++
        waitables[waitableIndex] = RealAsyncHost.Waitable(waitableIndex, job)
        PackedSubtaskResult(SubtaskState.Started, waitableIndex)
      }

      else -> PackedSubtaskResult(SubtaskState.Returned)
    }
  }

  fun taskReturn(value: Any? = Unit) {
    this.taskResult = value
  }
}

@BrevityInternalApi
inline fun <reified T : Resource> HostBridge.drop(id: Int): T {
  return dropInternal(id) as T
}

@BrevityInternalApi
class MemoryAllocator internal constructor(
  private val cabiRealloc: ExportFunction,
) {
  /** Invoke `cabi_realloc(originalAddress, originalSize, align, newSize)`. */
  fun allocate(byteCount: Int): Int = cabiRealloc.apply(0L, 0L, 0L, byteCount.toLong())[0].toInt()
}
