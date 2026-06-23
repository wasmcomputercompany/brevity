package dev.wasmo.brevity

object GuestBridge {
  private val idToResource = mutableMapOf<Int, Resource>()
  private var nextId = 4_040_000

  fun <T : Resource> toId(resource: T): Int {
    val id = nextId++
    idToResource[id] = resource
    return id
  }

  fun <T : Resource> fromId(id: Int, constructor: (Int) -> T): T {
    return constructor(id)
  }
}
