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

  inline fun <reified T : Resource> registerResourceHandle(
    noinline constructor: (id: Int) -> T,
  ) {
    registerResourceHandle(typeOf<T>(), constructor)
  }

  @PublishedApi
  internal fun registerResourceHandle(type: KType, createHandle: (id: Int) -> Resource) {
    typeToResourceHandle[type] = createHandle
  }

  fun <T : Resource> toId(resource: T): Int {
    val id = nextId++
    idToResource[id] = resource
    return id
  }

  inline fun <reified T : Resource> fromId(id: Int): T {
    return fromId(typeOf<T>(), id) as T
  }

  @PublishedApi
  internal fun fromId(type: KType, id: Int): Resource? {
    val createHandle = typeToResourceHandle[type]
    if (createHandle != null) return createHandle(id)
    return idToResource[id]
  }

  @PublishedApi
  internal fun dropInternal(id: Int): Resource? {
    return idToResource.remove(id)
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
