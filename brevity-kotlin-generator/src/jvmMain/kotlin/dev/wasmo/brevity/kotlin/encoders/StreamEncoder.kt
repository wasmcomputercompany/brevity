package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName as TypeNameKt
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.code.GuestPlatform

internal class StreamEncoder(
  type: TypeName,
  private val receiverType: TypeNameKt,
  private val streamType: TypeNameKt,
) : HandleEncoder(type) {
  context(codeBuilder: CodeBuilder, context: Context)
  override fun liftHandle(
    index: CodeBlock,
  ): CodeBlock {
    return when {
      codeBuilder.platform is GuestPlatform -> {
        CodeBlock.of(
          "%L.getImportedResource(%L) { %T().receiver }",
          codeBuilder.bridge,
          index,
          streamType,
        )
      }
      else -> {
        CodeBlock.of(
          "%L.getImportedResource<%T>(%L)",
          codeBuilder.bridge,
          receiverType,
          index,
        )
      }
    }
  }

  context(codeBuilder: CodeBuilder, context: Context)
  override fun lowerHandle(handle: CodeBlock) = CodeBlock.of("%L.rep", handle)
}
