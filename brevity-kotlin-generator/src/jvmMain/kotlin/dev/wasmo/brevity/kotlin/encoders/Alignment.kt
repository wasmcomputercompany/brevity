package dev.wasmo.brevity.kotlin.encoders

/** Returns the smallest int greater or equal to this, and that equally divides [alignment]. */
fun Int.alignTo(alignment: Int): Int =
  ((this + alignment - 1) / alignment) * alignment
