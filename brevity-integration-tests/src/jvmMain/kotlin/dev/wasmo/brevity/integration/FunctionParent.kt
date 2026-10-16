package dev.wasmo.brevity.integration

import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.Keyword
import dev.wasmo.brevity.Orientation

enum class FunctionParent(
  val identifier: Identifier,
  val keyword: Keyword,
  val orientation: Orientation,
) {
  WorldExport(
    identifier = Identifier("test-world"),
    keyword = Keyword.world,
    orientation = Orientation.Export,
  ),
  WorldImport(
    identifier = Identifier("test-world"),
    keyword = Keyword.world,
    orientation = Orientation.Import,
  ),
  InterfaceExport(
    identifier = Identifier("exported-interface"),
    keyword = Keyword.`interface`,
    orientation = Orientation.Export,
  ),
  InterfaceImport(
    identifier = Identifier("imported-interface"),
    keyword = Keyword.`interface`,
    orientation = Orientation.Import,
  ),
  ResourceExport(
    identifier = Identifier("exported-resource"),
    keyword = Keyword.resource,
    orientation = Orientation.Export,
  ),
  ResourceImport(
    identifier = Identifier("imported-resource"),
    keyword = Keyword.resource,
    orientation = Orientation.Import,
  ),
  ;

  val isWorld: Boolean
    get() = this == WorldExport || this == WorldImport
  val isResource: Boolean
    get() = this == ResourceExport || this == ResourceImport
  val serviceName: Identifier
    get() = when (this) {
      ResourceExport, ResourceImport -> Identifier(identifier.name + "-wrapper")
      else -> identifier
    }

  fun export(): FunctionParent = when (this) {
    WorldImport -> WorldExport
    InterfaceImport -> InterfaceExport
    ResourceImport -> ResourceExport
    else -> this
  }
}
