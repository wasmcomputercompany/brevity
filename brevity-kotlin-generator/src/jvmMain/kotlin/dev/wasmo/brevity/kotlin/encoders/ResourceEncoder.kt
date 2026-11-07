package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.kotlin.code.CodeBuilder

internal class ResourceEncoder(
  private val declaredType: TypeName.Declared,
  private val abiClassName: ClassName,
) : HandleEncoder(declaredType) {
  context(codeBuilder: CodeBuilder, context: Context)
  override fun liftHandle(
    index: CodeBlock,
  ): CodeBlock = codeBuilder.platform.liftResource(
    id = index,
    resourceApiName = abiClassName,
    resourceImplementationName = context.typeNamer.implementation(declaredType),
    orientation = context.getOrientation(declaredType),
  )

  context(codeBuilder: CodeBuilder, context: Context)
  override fun lowerHandle(
    handle: CodeBlock,
  ) = codeBuilder.platform.lowerResource(
    resource = handle,
    resourceType = declaredType,
    orientation = context.getOrientation(declaredType),
  )
}
