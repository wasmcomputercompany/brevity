package dev.wasmo.brevity

enum class Orientation(
  val identifier: Identifier,
) {
  Export(Identifier("export")),
  Import(Identifier("import")),
  ;

  val mirror: Orientation
    get() = when (this) {
      Export -> Import
      Import -> Export
    }
}
