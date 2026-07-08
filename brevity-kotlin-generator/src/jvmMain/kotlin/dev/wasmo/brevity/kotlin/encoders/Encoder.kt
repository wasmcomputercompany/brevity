package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.kotlin.generator.CoreType
import dev.wasmo.brevity.kotlin.generator.handleName
import dev.wasmo.brevity.kotlin.generator.kotlinApi
import dev.wasmo.brevity.kotlin.generator.kotlinCoreType

abstract class Encoder {
  abstract val coreType: CoreType

  /** Lift an ABI value like a memory address to an API value like a resource instance. */
  abstract fun coreTypeToValue(bridge: CodeBlock, coreType: CodeBlock): CodeBlock

  /** Lower an API value like a resource instance to an ABI value like a memory address. */
  abstract fun valueToCoreType(bridge: CodeBlock, value: CodeBlock): CodeBlock

  open fun longToValue(bridge: CodeBlock, coreType: CodeBlock): CodeBlock =
    coreTypeToValue(bridge, coreType)

  open fun valueToLong(bridge: CodeBlock, value: CodeBlock): CodeBlock =
    valueToLong(bridge, value)
}

class FallbackEncoder(
  private val type: TypeName,
  override val coreType: CoreType,
) : Encoder() {
  override fun coreTypeToValue(bridge: CodeBlock, coreType: CodeBlock) =
    CodeBlock.of("%L as %T", coreType, type.kotlinApi)

  override fun valueToCoreType(bridge: CodeBlock, value: CodeBlock) =
    CodeBlock.of("%L as %T", value, coreType.kotlinCoreType)
}

class ResourceEncoder(
  private val type: TypeName.Declared,
) : Encoder() {
  override val coreType: CoreType
    get() = CoreType.I32

  override fun coreTypeToValue(bridge: CodeBlock, coreType: CodeBlock) =
    CodeBlock.of("%L.fromId(%L, ::%T)", bridge, coreType, type.handleName)

  override fun valueToCoreType(bridge: CodeBlock, value: CodeBlock) =
    CodeBlock.of("%L.toId(%L)", bridge, value)
}

class ListEncoder(
  private val type: TypeName,
) : Encoder() {
  override val coreType: CoreType
    get() = CoreType.Pointer

  override fun coreTypeToValue(bridge: CodeBlock, coreType: CodeBlock) =
    CodeBlock.of("%L as %T", coreType, type.kotlinApi)

  override fun valueToCoreType(bridge: CodeBlock, value: CodeBlock) =
    CodeBlock.of("%L as %T", value, coreType.kotlinCoreType)
}

object StringEncoder1 : Encoder() {
  override val coreType: CoreType
    get() = CoreType.Pointer

  override fun coreTypeToValue(bridge: CodeBlock, coreType: CodeBlock) =
    CodeBlock.of("%L as %T", coreType, TypeName.String.kotlinApi)

  override fun valueToCoreType(bridge: CodeBlock, value: CodeBlock) =
    CodeBlock.of("%L as %T", value, coreType.kotlinCoreType)
}
