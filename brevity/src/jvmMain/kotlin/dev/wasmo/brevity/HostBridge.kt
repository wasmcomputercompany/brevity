package dev.wasmo.brevity

class HostBridge {
  private val idToResource = mutableMapOf<Long, Resource>()
  private var nextId = 6_060_000L

  fun <T : Resource> toId(resource: T): Long {
    val id = nextId++
    idToResource[id] = resource
    return id
  }

  fun <T : Resource> fromId(id: Long, constructor: (Int) -> T): T {
    return constructor(id.toInt())
  }
}
