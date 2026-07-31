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

fun CoreType.fromBits(sourceType: CoreType, value: CodeBlock): CodeBlock {
  return when (sourceType) {
    this -> value

    CoreType.I64 -> {
      when (this) {
        CoreType.F32 -> CodeBlock.of("%T.fromBits(%L.toInt())", Symbols.Kotlin.Float, value)
        CoreType.F64 -> CodeBlock.of("%T.fromBits(%L)", Symbols.Kotlin.Double, value)
        CoreType.I32, CoreType.Pointer -> CodeBlock.of("%L.toInt()", value)
        else -> error("unexpected type conversion: $sourceType -> $this")
      }
    }

    CoreType.I32 -> {
      when (this) {
        CoreType.F32 -> CodeBlock.of("%T.fromBits(%L)", Symbols.Kotlin.Float, value)
        CoreType.Pointer -> value
        else -> error("unexpected type conversion: $sourceType -> $this")
      }
    }

    else -> error("unexpected source type: $sourceType")
  }
}

fun CoreType.toBits(targetType: CoreType, value: CodeBlock): CodeBlock {
  return when (targetType) {
    this -> value

    CoreType.I64 -> {
      when (this) {
        CoreType.F32 -> CodeBlock.of("%L.toBits().toLong()", value)
        CoreType.F64 -> CodeBlock.of("%L.toBits()", value)
        CoreType.I32, CoreType.Pointer -> CodeBlock.of("%L.toLong()", value)
        else -> error("unexpected type conversion: $this -> $targetType")
      }
    }

    CoreType.I32 -> {
      when (this) {
        CoreType.F32 -> CodeBlock.of("%L.toBits()", value)
        CoreType.Pointer -> value
        else -> error("unexpected type conversion: $this -> $targetType")
      }
    }

    else -> error("unexpected type conversion: $this -> $targetType")
  }
}

val CoreType.zero: CodeBlock
  get() = when (this){
    CoreType.F32 -> CodeBlock.of("0.0f")
    CoreType.F64 -> CodeBlock.of("0.0")
    CoreType.I32 -> CodeBlock.of("0")
    CoreType.I64 -> CodeBlock.of("0L")
    CoreType.Pointer -> CodeBlock.of("0")
  }
