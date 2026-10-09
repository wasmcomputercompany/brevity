package dev.wasmo.brevity

enum class Orientation {
  Export,
  Import,
  ;

  val mirror: Orientation
    get() = when (this) {
      Export -> Import
      Import -> Export
    }
}
