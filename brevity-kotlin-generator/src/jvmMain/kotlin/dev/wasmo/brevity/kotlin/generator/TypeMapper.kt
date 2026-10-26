package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.TypeName as KtTypeName
import dev.wasmo.brevity.kotlin.code.CodeBuilder

/**
 * Maps `.wit` types to custom Kotlin types.
 */
interface TypeMapper {
  /** The custom Kotlin type that is being mapped to. */
  val type: KtTypeName

  context(codeBuilder: CodeBuilder)
  fun toWit(value: CodeBlock): CodeBlock

  context(codeBuilder: CodeBuilder)
  fun fromWit(value: CodeBlock): CodeBlock
}

class IdentityTypeMapper(
  override val type: KtTypeName,
) : TypeMapper {
  context(codeBuilder: CodeBuilder)
  override fun toWit(value: CodeBlock) = value

  context(codeBuilder: CodeBuilder)
  override fun fromWit(value: CodeBlock) = value
}

class RealTypeMapper(
  private val adapter: MemberName,
  override val type: KtTypeName,
) : TypeMapper {
  /** Returns a code block with [value] converted to its ABI type, if necessary. */
  context(codeBuilder: CodeBuilder)
  override fun toWit(value: CodeBlock): CodeBlock {
    val witValueName = codeBuilder.newName("witValue")
    codeBuilder.addStatement(
      "val %N = %M.toWit(%L)",
      witValueName,
      adapter,
      value,
    )
    return CodeBlock.of("%L", witValueName)
  }

  /** Returns a code block with [value] converted from its ABI type, if necessary. */
  context(codeBuilder: CodeBuilder)
  override fun fromWit(value: CodeBlock): CodeBlock {
    return CodeBlock.of(
      "%M.fromWit(%L)",
      adapter,
      value,
    )
  }
}
