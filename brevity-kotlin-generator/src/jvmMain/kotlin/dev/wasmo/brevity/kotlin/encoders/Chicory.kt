package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FLOAT
import dev.wasmo.brevity.kotlin.generator.Symbols

val CoreType.valType: CodeBlock
  get() = when (this) {
    CoreType.F32 -> CodeBlock.of("%T.F32", Symbols.ChicoryRuntime.ValType)
    CoreType.F64 -> CodeBlock.of("%T.F64", Symbols.ChicoryRuntime.ValType)
    CoreType.I32 -> CodeBlock.of("%T.I32", Symbols.ChicoryRuntime.ValType)
    CoreType.I64 -> CodeBlock.of("%T.I64", Symbols.ChicoryRuntime.ValType)
    CoreType.Pointer -> CodeBlock.of("%T.I32", Symbols.ChicoryRuntime.ValType)
  }

/** Everything in Chicory is a [Long], so we need to convert core types. */
fun longToCoreType(
  arrayName: String,
  arrayIndex: Int,
  coreType: CoreType,
): CodeBlock {
  return when (coreType) {
    CoreType.F32 -> CodeBlock.of("%T.fromBits(%N[%L])", DOUBLE, arrayName, arrayIndex)
    CoreType.F64 -> CodeBlock.of("%T.fromBits(%N[%L].toInt())", FLOAT, arrayName, arrayIndex)
    CoreType.I32 -> CodeBlock.of("%N[%L].toInt()", arrayName, arrayIndex)
    CoreType.I64 -> CodeBlock.of("%N[%L]", arrayName, arrayIndex)
    CoreType.Pointer -> CodeBlock.of("%N[%L].toInt()", arrayName, arrayIndex)
  }
}

/** Everything in Chicory is a [Long], so we need to convert core types. */
fun coreTypeToLong(
  value: CodeBlock,
  coreType: CoreType,
): CodeBlock {
  return when (coreType) {
    CoreType.F32 -> CodeBlock.of("%L.toBits().toLong()", value)
    CoreType.F64 -> CodeBlock.of("%L.toBits()", value)
    CoreType.I32 -> CodeBlock.of("%L.toLong()", value)
    CoreType.I64 -> value
    CoreType.Pointer -> CodeBlock.of("%L.toLong()", value)
  }
}
