package dev.wasmo.brevity.kotlin.encoders

sealed class CoreType {
  object I32 : CoreType()
  object I64 : CoreType()
  object F32 : CoreType()
  object F64 : CoreType()
  object Pointer : CoreType()
}
