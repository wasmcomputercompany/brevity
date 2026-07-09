package dev.wasmo.brevity.kotlin.encoders

import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.kotlin.generator.handleName
import dev.wasmo.brevity.kotlin.generator.kotlinApi
import dev.wasmo.brevity.kotlin.generator.kotlinCoreType

abstract class Encoder {
  abstract val coreTypes: List<CoreType>

  open val nameHints: List<Identifier>?
    get() = null

  /** Lift an ABI value like a memory address to an API value like a resource instance. */
  abstract fun EncodeBuilder.coreTypeToValue()

  /** Lower an API value like a resource instance to an ABI value like a memory address. */
  abstract fun EncodeBuilder.valueToCoreType()
}

class FallbackEncoder(
  private val type: TypeName,
  val coreType: CoreType,
) : Encoder() {
  override val coreTypes = listOf(coreType)

  override fun EncodeBuilder.coreTypeToValue() {
    put("%L as %T", take(), type.kotlinApi)
  }

  override fun EncodeBuilder.valueToCoreType() {
    put("%L as %T", take(), coreType.kotlinCoreType)
  }
}

class ResourceEncoder(
  private val type: TypeName.Declared,
) : Encoder() {
  override val coreTypes = listOf(CoreType.I32)

  override fun EncodeBuilder.coreTypeToValue() {
    put("%L.fromId(%L, ::%T)", bridge, take(), type.handleName)
  }

  override fun EncodeBuilder.valueToCoreType() {
    put("%L.toId<%T>(%L)", bridge, type.kotlinApi, take())
  }
}

class ListEncoder(
  private val type: TypeName,
) : Encoder() {
  override val coreTypes = listOf(CoreType.Pointer)

  override fun EncodeBuilder.coreTypeToValue() {
    put("%L as %T", take(), type.kotlinApi)
  }

  override fun EncodeBuilder.valueToCoreType() {
    put("%L as %T", take(), CoreType.Pointer.kotlinCoreType)
  }
}

