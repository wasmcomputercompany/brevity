package dev.wasmo.brevity

import com.dylibso.chicory.runtime.ExportFunction

@BrevityInternalApi
class MemoryAllocator internal constructor(
  private val cabiRealloc: ExportFunction,
) {
  /** Invoke `cabi_realloc(originalAddress, originalSize, align, newSize)`. */
  fun allocate(byteCount: Int): Int = cabiRealloc.apply(0L, 0L, 0L, byteCount.toLong())[0].toInt()
}

/** Returns the smallest int greater or equal to this, and that equally divides [alignment]. */
@BrevityInternalApi
fun Int.alignTo(alignment: Int): Int =
  ((this + alignment - 1) / alignment) * alignment
