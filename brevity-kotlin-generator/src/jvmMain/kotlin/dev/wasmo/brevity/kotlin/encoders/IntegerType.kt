package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.BYTE
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.SHORT
import com.squareup.kotlinpoet.TypeName

enum class IntegerType {
  S8 {
    override val byteCount: Int
      get() = 1

    override val kotlinType: TypeName
      get() = BYTE
  },
  S16 {
    override val byteCount: Int
      get() = 2

    override val kotlinType: TypeName
      get() = SHORT
  },
  S32 {
    override val byteCount: Int
      get() = 4

    override val kotlinType: TypeName
      get() = INT
  },
  S64 {
    override val byteCount: Int
      get() = 8

    override val kotlinType: TypeName
      get() = LONG
  },
  ;

  abstract val byteCount: Int
  abstract val kotlinType: TypeName

  companion object {
    fun discriminant(caseCount: Int): IntegerType {
      return when {
        caseCount <= UByte.MAX_VALUE.toInt() -> S8
        caseCount <= UShort.MAX_VALUE.toInt() -> S16
        else -> S32
      }
    }
  }
}

val CoreType.integerType: IntegerType
  get() = when (this) {
    CoreType.F32 -> IntegerType.S32
    CoreType.F64 -> IntegerType.S64
    CoreType.I32 -> IntegerType.S32
    CoreType.I64 -> IntegerType.S64
    CoreType.Pointer -> IntegerType.S32
  }
