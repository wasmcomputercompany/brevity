package dev.wasmo.brevity.kotlin.encoders

enum class IntegerType {
  S8 {
    override val byteCount: Int
      get() = 1
  },
  S16 {
    override val byteCount: Int
      get() = 2
  },
  S32 {
    override val byteCount: Int
      get() = 4
  },
  S64 {
    override val byteCount: Int
      get() = 8
  },
  ;

  abstract val byteCount: Int

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
