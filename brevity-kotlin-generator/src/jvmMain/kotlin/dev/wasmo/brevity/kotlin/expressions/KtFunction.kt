package dev.wasmo.brevity.kotlin.expressions

import dev.wasmo.brevity.kotlin.code.CodeBuilder

interface KtFunction {
  /**
   * Emits code to invoke this function, and returns an expression holding its result. This may be
   * a regular function call, or an adapted function call like a lift or a lower.
   */
  context(codeBuilder: CodeBuilder)
  fun call(
    receiver: KtExpression?,
    parameters: List<KtExpression>,
  ): KtExpression
}
