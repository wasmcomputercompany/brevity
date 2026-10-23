package dev.wasmo.brevity

import com.dylibso.chicory.runtime.ExportFunction
import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.runtime.Memory
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlinx.coroutines.runBlocking

@BrevityInternalApi
class HostBridge {
  private val typeToResourceHandle = mutableMapOf<KType, (Int) -> Resource>()
  private val indexToResource = mutableMapOf<Int, Resource>()
  private var nextIndex = 6_060_000
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

  fun newResource(handle: Resource): Int {
    val result = nextIndex++
    indexToResource[result] = handle
    return result
  }

  inline fun <reified T : Resource> registerResourceHandle(
    noinline constructor: (id: Int) -> T,
  ) {
    registerResourceHandle(typeOf<T>(), constructor)
  }

  @PublishedApi
  internal fun registerResourceHandle(type: KType, createHandle: (id: Int) -> Resource) {
//    typeToResourceHandle[type] = createHandle
  }

  fun <T : Resource> toIndex(resource: T): Int {
    val id = nextIndex++
    indexToResource[id] = resource
    return id
  }

  inline fun <reified T : Resource> fromIndex(index: Int): T {
    return fromIndex(typeOf<T>(), index) as T
  }

  @PublishedApi
  internal fun fromIndex(type: KType, index: Int): Resource? {
//    val createHandle = typeToResourceHandle[type]
//    if (createHandle != null) return createHandle(id)
    return indexToResource[index]
  }

  @PublishedApi
  internal fun dropInternal(id: Int): Resource? {
    return indexToResource.remove(id)
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
