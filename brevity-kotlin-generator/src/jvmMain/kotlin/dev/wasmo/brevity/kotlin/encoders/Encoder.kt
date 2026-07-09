package dev.wasmo.brevity.kotlin.encoders

import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.TypeName
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

/** Fake encoder for all the types we don't actually implement yet. */
class FallbackEncoder(
  private val type: TypeName,
  val coreType: CoreType,
) : Encoder() {
  override val coreTypes = listOf(coreType)

  override fun EncodeBuilder.coreTypeToValue() {
    put("(%L as %T)", take(), type.kotlinApi)
  }

  override fun EncodeBuilder.valueToCoreType() {
    put("(%L as %T)", take(), coreType.kotlinCoreType)
  }
}

class ListEncoder(
  private val type: TypeName,
) : Encoder() {
  override val coreTypes = listOf(CoreType.Pointer)

  override fun EncodeBuilder.coreTypeToValue() {
    put("(%L as %T)", take(), type.kotlinApi)
  }

  override fun EncodeBuilder.valueToCoreType() {
    put("(%L as %T)", take(), CoreType.Pointer.kotlinCoreType)
  }
}

class ResourceEncoder(
  private val type: TypeName.Declared,
) : Encoder() {
  override val coreTypes = listOf(CoreType.I32)

  override fun EncodeBuilder.coreTypeToValue() {
    put(platform.liftResource(take(), type))
  }

  override fun EncodeBuilder.valueToCoreType() {
    put(platform.lowerResource(take(), type))
  }
}

/** Stores a string as two pointers: an address and a byte count. */
object StringEncoder : Encoder() {
  override val coreTypes: List<CoreType>
    get() = listOf(CoreType.Pointer, CoreType.Pointer)

  override val nameHints: List<Identifier>
    get() = listOf(Identifier("pointer"), Identifier("byte-count"))

  override fun EncodeBuilder.coreTypeToValue() {
    put(platform.loadString(take(), take()))
  }

  override fun EncodeBuilder.valueToCoreType() {
    val (address, size) = platform.storeString(take())
    put(address)
    put(size)
  }
}
