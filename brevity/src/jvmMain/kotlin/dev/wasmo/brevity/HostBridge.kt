package dev.wasmo.brevity

import com.dylibso.chicory.runtime.ExportFunction
import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.runtime.Memory

class HostBridge {
  private val idToResource = mutableMapOf<Int, Resource>()
  private var nextId = 6_060_000
  private lateinit var _memory: Memory
  private lateinit var cabiRealloc: ExportFunction

  val memory: Memory
    get() = _memory

  fun init(instance: Instance) {
    this._memory = instance.memory()
    this.cabiRealloc = instance.export("cabi_realloc")
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

  /** Invoke `cabi_realloc(originalAddress, originalSize, align, newSize)`. */
  fun allocate(byteCount: Int): Int {
    return cabiRealloc.apply(0L, 0L, 0L, byteCount.toLong())[0].toInt()
  }
}

inline operator fun <reified T : Resource> HostBridge.get(id: Int): T {
  return getInternal(id) as T
}
