package dev.wasmo.brevity.kotlin.expressions

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeName as KtTypeName
import com.squareup.kotlinpoet.UNIT
import dev.wasmo.brevity.kotlin.code.CodeBuilder

/**
 * A code block with associated name and type information.
 */
interface KtExpression {
  val code: CodeBlock

  /** The static type of this expression. */
  val type: KtTypeName

  /** True if assigning this expression to a local variable is not necessary. */
  val immediate: Boolean
    get() = false

  /** Returns a name hint for this value, used by callers to create local variables for it. */
  val nameHint: String

  companion object {
    val Unit = object : KtExpression {
      override val code: CodeBlock
        get() = CodeBlock.of("%T", UNIT)
      override val type: KtTypeName
        get() = UNIT
      override val nameHint: String
        get() = "unit"
      override val immediate: Boolean
        get() = true
    }
  }
}

data class CodeBlockExpression(
  override val type: KtTypeName,
  override val nameHint: String,
  override val code: CodeBlock,
  override val immediate: Boolean = false,
) : KtExpression

/** Returns an expression that's immediate, introducing a local variable if necessary. */
context(codeBuilder: CodeBuilder)
fun KtExpression.makeImmediate(): KtExpression {
  if (immediate) return this

  val name = codeBuilder.newName(nameHint)
  codeBuilder.addStatement("val %N = %L", name, code)
  return CodeBlockExpression(
    type = type,
    nameHint = nameHint,
    code = CodeBlock.of("%N", name),
    immediate = true,
  )
}

val ParameterSpec.valueExpression: CodeBlockExpression
  get() = CodeBlockExpression(
    type = type,
    nameHint = name,
    code = CodeBlock.of("%N", name),
    immediate = true,
  )
