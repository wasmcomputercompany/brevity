package dev.wasmo.brevity

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

suspend inline fun <T, R> Iterable<T>.mapAsync(
  crossinline transform: suspend (T) -> R,
): List<R> {
  val mapOfDeferredResults = coroutineScope {
    map { element ->
      async {
        transform(element)
      }
    }
  }
  return mapOfDeferredResults.awaitAll()
}
