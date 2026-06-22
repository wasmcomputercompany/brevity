package dev.wasmo.brevity

class Bridge {
  private val idToResource = mutableMapOf<Int, Resource>()
  private var nextId = 999_000

  fun <T : Resource> toId(resource: T): Int {
    val id = nextId++
    idToResource[id] = resource
    return id
  }

  fun <T : Resource> fromId(id: Int, constructor: (Int) -> T): T {
    return constructor(id)
  }
}

interface Resource : AutoCloseable {
  override fun close() {
  }
}
