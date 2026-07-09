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
