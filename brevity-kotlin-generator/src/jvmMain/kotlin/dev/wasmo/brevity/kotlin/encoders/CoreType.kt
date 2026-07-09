package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock
import dev.wasmo.brevity.kotlin.generator.Symbols

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

val CoreType.valType: CodeBlock
  get() = when (this) {
    CoreType.F32 -> CodeBlock.of("%T.F32", Symbols.ChicoryRuntime.ValType)
    CoreType.F64 -> CodeBlock.of("%T.F64", Symbols.ChicoryRuntime.ValType)
    CoreType.I32 -> CodeBlock.of("%T.I32", Symbols.ChicoryRuntime.ValType)
    CoreType.I64 -> CodeBlock.of("%T.I64", Symbols.ChicoryRuntime.ValType)
    CoreType.Pointer -> CodeBlock.of("%T.I32", Symbols.ChicoryRuntime.ValType)
  }
