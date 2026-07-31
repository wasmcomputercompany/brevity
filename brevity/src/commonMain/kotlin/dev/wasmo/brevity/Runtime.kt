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
class FlatSink(i32Count: Int, i64Count: Int) {
  private var nextI32 = 0
  private val i32s = IntArray(i32Count)
  private var nextI64 = 0
  private val i64s = LongArray(i64Count)

  fun put(v: Any) {
  }

  // TODO(jwilson): restore these overloads once we've finished callers.
  //  fun put(v: Int) {
  //    i32s[nextI32++] = v
  //  }
  //  fun put(v: Long) {
  //    i64s[nextI64++] = v
  //  }
  //  fun put(v: Float) {
  //    i32s[nextI32++] = v.toBits()
  //  }
  //  fun put(v: Double) {
  //    i64s[nextI64++] = v.toBits()
  //  }
}
