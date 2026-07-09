package dev.wasmo.brevity

import com.dylibso.chicory.runtime.ExportFunction
import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.runtime.Memory

class HostBridge {
  private val idToResource = mutableMapOf<Long, Resource>()
  private var nextId = 6_060_000L
  private lateinit var _memory: Memory
  private lateinit var cabiRealloc: ExportFunction

  val memory: Memory
    get() = _memory

  fun init(instance: Instance) {
    this._memory = instance.memory()
    this.cabiRealloc = instance.export("cabi_realloc")
  }

  fun <T : Resource> toId(resource: T): Long {
    val id = nextId++
    idToResource[id] = resource
    return id
  }

  fun <T : Resource> fromId(id: Long, constructor: (Int) -> T): T {
    return constructor(id.toInt())
  }

  @PublishedApi
  internal fun getInternal(id: Long): Resource? {
    return idToResource[id]
  }

  /** Invoke `cabi_realloc(originalAddress, originalSize, newSize)`. */
  fun allocate(byteCount: Int): Int {
    return cabiRealloc.apply(0L, 0L, byteCount.toLong())[0].toInt()
  }
}

inline operator fun <reified T : Resource> HostBridge.get(id: Long): T {
  return getInternal(id) as T
}
