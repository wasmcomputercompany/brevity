package dev.wasmo.brevity

import com.dylibso.chicory.runtime.ExportFunction
import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.runtime.Memory

@BrevityInternalApi
class HostBridge {
  private val idToResource = mutableMapOf<Int, Resource>()
  private var nextId = 6_060_000
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

  fun <T : Resource> toId(resource: T): Int {
    val id = nextId++
    idToResource[id] = resource
    return id
  }

  @PublishedApi
  internal fun getInternal(id: Int): Resource? {
    return idToResource[id]
  }

  @PublishedApi
  internal fun dropInternal(id: Int): Resource? {
    return idToResource.remove(id)
  }

  fun launchTask(block: suspend () -> Unit): PackedAsyncResult {
    return PackedAsyncResult(CallbackCode.Exit)
  }

  fun resumeTask(eventCode: Int, p1: Int, p2: Int): PackedAsyncResult {
    return PackedAsyncResult(CallbackCode.Exit)
  }

  fun taskReturn(value: Any? = Unit) {
    this.taskResult = value
  }

  suspend fun <T> awaitTaskResult(): T {
    return taskResult as T
  }
}

@BrevityInternalApi
inline operator fun <reified T : Resource> HostBridge.get(id: Int): T {
  return getInternal(id) as T
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
