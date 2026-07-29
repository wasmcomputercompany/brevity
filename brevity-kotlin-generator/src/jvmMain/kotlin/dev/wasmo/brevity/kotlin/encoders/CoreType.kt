package dev.wasmo.brevity.kotlin.encoders

sealed class CoreType {
  object I32 : CoreType()
  object I64 : CoreType()
  object F32 : CoreType()
  object F64 : CoreType()
  object Pointer : CoreType()
}

val CoreType.byteCount: Int
  get() = when (this) {
    CoreType.I64, CoreType.F64 -> 8
    else -> 4
  }

val CoreType.alignment: Int
  get() = when (this) {
    CoreType.I64, CoreType.F64 -> 8
    else -> 4
  }

/** Returns the smallest core type that can store this or [other]. */
internal fun CoreType.bitwiseUnion(other: CoreType): CoreType {
  return when {
    this == other -> this
    byteCount == 4 && other.byteCount == 4 -> CoreType.I32
    else -> CoreType.I64
  }
}

/** Returns a list of core types that can store each element of this, respectively. */
internal fun Iterable<List<CoreType>>.bitwiseUnion(): List<CoreType> {
  return buildList {
    for (case in this@bitwiseUnion) {
      for ((index, type) in case.withIndex()) {
        if (index < size) {
          this[index] = this[index].bitwiseUnion(type)
        } else {
          add(type)
        }
      }
    }
  }
}
