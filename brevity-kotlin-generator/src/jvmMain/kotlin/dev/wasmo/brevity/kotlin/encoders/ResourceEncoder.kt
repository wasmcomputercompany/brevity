package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.kotlin.code.LowerFlatIo
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.code.MemoryAllocator

internal class ResourceEncoder(
  override val type: TypeName.Declared,
  private val abiClassName: ClassName,
) : Encoder() {
  override val coreTypes = listOf(CoreType.I32)

  override val byteCount: Int
    get() = CoreType.I32.byteCount

  override val alignment: Int
    get() = CoreType.I32.alignment

  context(codeBuilder: CodeBuilder, context: Context)
  override fun load(
    baseAddress: CodeBlock,
    offset: Int,
  ): CodeBlock {
    return liftResource(
      index = codeBuilder.platform.load(
        baseAddress,
        offset,
        CoreType.Pointer,
      ),
    )
  }

  context(codeBuilder: CodeBuilder, context: Context)
  override fun store(
    memoryAllocator: MemoryAllocator?,
    baseAddress: CodeBlock,
    offset: Int,
    value: CodeBlock,
  ) {
    codeBuilder.platform.store(
      baseAddress,
      offset,
      CoreType.Pointer,
      lowerResource(
        resource = value,
      ),
    )
  }

  context(codeBuilder: CodeBuilder, context: Context)
  override fun liftFlat(liftFlatIo: LiftFlatIo) {
    liftFlatIo.put(
      liftResource(
        index = liftFlatIo.take(),
      ),
    )
  }

  context(codeBuilder: CodeBuilder, context: Context)
  override fun lowerFlat(
    memoryAllocator: MemoryAllocator?,
    lowerFlatIo: LowerFlatIo,
    value: CodeBlock,
  ) {
    lowerFlatIo.put(lowerResource(resource = value))
  }

  context(codeBuilder: CodeBuilder, context: Context)
  fun liftResource(
    index: CodeBlock,
  ): CodeBlock = codeBuilder.platform.liftResource(
    id = index,
    resourceApiName = abiClassName,
    resourceImplementationName = context.typeNamer.implementation(type),
    orientation = context.getOrientation(type),
  )

  context(codeBuilder: CodeBuilder, context: Context)
  fun lowerResource(
    resource: CodeBlock,
  ) = codeBuilder.platform.lowerResource(
    resource = resource,
    resourceType = type,
    orientation = context.getOrientation(type),
  )
}
