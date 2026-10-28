package dev.wasmo.brevity

data class Quad<A, B, C, D>(
  val a: A,
  val b: B,
  val c: C,
  val d: D,
) {
  override fun toString() = "($a, $b, $c, $d)"
}

class Borrow<T : Any>(val value: T) {
  override fun equals(other: Any?) = other is Borrow<*> && other.value == value

  override fun hashCode() = value.hashCode()

  override fun toString() = "Borrow($value)"
}

interface Stream<T : Any> {
  fun next(): T
}

sealed interface Result<out S, out F> {
  data class Ok<out S, out F>(
    val value: S,
  ) : Result<S, F>

  data class Error<out S, out F>(
    val value: F,
  ) : Result<S, F>
}

/** This is used internally by Brevity to implement flattening. */
class CallBuilder(
  valueCount: Int,
) {
  private var next = 0
  private val values = LongArray(valueCount)

  fun put(v: Int) {
    put(v.toLong())
  }

  fun put(v: Long) {
    values[next++] = v
  }

  fun put(v: Float) {
    put(v.toBits())
  }

  fun put(v: Double) {
    put(v.toBits())
  }

  fun skip(count: Int) {
    next += count
  }

  fun takeI32() = takeI64().toInt()
  fun takeI64() = values[next++ - values.size]
  fun takeF32() = Float.fromBits(takeI32())
  fun takeF64() = Double.fromBits(takeI64())
}
