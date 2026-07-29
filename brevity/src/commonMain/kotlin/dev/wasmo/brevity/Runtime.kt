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
