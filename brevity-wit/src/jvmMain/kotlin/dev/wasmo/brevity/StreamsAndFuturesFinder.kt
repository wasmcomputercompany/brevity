package dev.wasmo.brevity

import dev.wasmo.brevity.ir.IrFunction

class Type

/**
 * As implemented in wit-bindgen's `find_futures_and_streams`, we traverse all parameter types
 * and any return type, recursively, collecting types that are either `stream<T>` or
 * `<future<T>`. Results must be returned in depth-first order.
 */
class StreamsAndFuturesFinder(
  private val declarationIndex: DeclarationIndex,
) {
  fun find(function: IrFunction): List<TypeName> {
    val sequence = sequence {
      for (parameter in function.parameters) {
        declarationIndex.collectTypesDepthFirst(parameter.type)
      }
      function.returnType?.let { declarationIndex.collectTypesDepthFirst(it) }
    }

    return sequence
      .filter { it is TypeName.Future || it is TypeName.Stream }
      .toList()
  }
}

