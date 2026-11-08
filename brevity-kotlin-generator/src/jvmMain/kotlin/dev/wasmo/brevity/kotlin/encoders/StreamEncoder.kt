package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName as TypeNameKt
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.code.GuestPlatform

internal class StreamEncoder(
  type: TypeName,
  private val streamType: TypeNameKt,
  private val pairType: TypeNameKt,
) : HandleEncoder(type) {
  context(codeBuilder: CodeBuilder, context: Context)
  override fun liftHandle(
    index: CodeBlock,
  ): CodeBlock {
    return when {
      codeBuilder.platform is GuestPlatform -> {
        CodeBlock.of(
          "%L.getImportedResource(%L) { %T().receive }",
          codeBuilder.bridge,
          index,
          pairType,
        )
      }
      else -> {
        CodeBlock.of(
          "%L.getImportedResource<%T>(%L)",
          codeBuilder.bridge,
          streamType,
          index,
        )
      }
    }
  }

  context(codeBuilder: CodeBuilder, context: Context)
  override fun lowerHandle(handle: CodeBlock) = CodeBlock.of("%L.rep", handle)
}
