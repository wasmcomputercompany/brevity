package dev.wasmo.brevity

import com.dylibso.chicory.runtime.ExportFunction
import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.runtime.Memory
import kotlinx.coroutines.runBlocking

@BrevityInternalApi
class HostBridge {
  private val repToResource = mutableMapOf<Int, Resource>()
  private var nextRep = 6_060_000
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

  fun <T : Resource> putResource(resource: T): Int {
    val rep = nextRep++
    repToResource[rep] = resource
    return rep
  }

  fun <T : Resource> getResource(rep: Int): T {
    val resource = repToResource[rep] ?: error("unexpected rep: $rep")
    return resource as T
  }

  @PublishedApi
  internal fun dropInternal(rep: Int): Resource? {
    return repToResource.remove(rep)
  }

  fun launchSubtask(block: suspend () -> Unit): PackedSubtaskResult {
    runBlocking {
      block()
    }
    return PackedSubtaskResult(SubtaskState.Returned)
  }

  fun taskReturn(value: Any? = Unit) {
    this.taskResult = value
  }

  suspend fun <T> awaitTaskResult(): T {
    return taskResult as T
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
