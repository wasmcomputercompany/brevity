package dev.wasmo.brevity.integration

import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.Keyword

enum class FunctionParent(
  val keyword: Keyword,
  val identifier: Identifier,
) {
  World(Keyword.world, Identifier("test-world")),
  Interface(Keyword.`interface`, Identifier("test-interface")),
  Resource(Keyword.resource, Identifier("test-resource"));
  // TODO: interfaces with plain names.
}
