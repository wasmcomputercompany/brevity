package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.kotlin.generator.kotlinApi
import dev.wasmo.brevity.kotlin.generator.kotlinCoreType

abstract class Encoder {
  abstract val coreTypes: List<CoreType>
  abstract val byteCount: Int
  abstract val alignment: Int

  open val nameHints: List<Identifier>?
    get() = null

  /** Loads a value from memory at [baseAddress] + [offset]. */
  abstract fun BridgeBuilder.load(baseAddress: CodeBlock, offset: Int): CodeBlock

  /** Stores [value] in memory at [baseAddress] + [offset]. */
  abstract fun BridgeBuilder.store(baseAddress: CodeBlock, offset: Int, value: CodeBlock)

  /** Lift an ABI value like a memory address to an API value like a resource instance. */
  abstract fun FlatEncoder.liftFlat()

  /** Lower an API value like a resource instance to an ABI value like a memory address. */
  abstract fun FlatEncoder.lowerFlat()
}

/** Fake encoder for all the types we don't actually implement yet. */
class FallbackEncoder(
  private val type: TypeName,
  val coreType: CoreType,
) : Encoder() {
  override val coreTypes = listOf(coreType)

  override val byteCount: Int
    get() = coreType.byteCount

  override val alignment: Int
    get() = coreType.alignment

  override fun BridgeBuilder.load(
    baseAddress: CodeBlock,
    offset: Int,
  ) = CodeBlock.of(
    "TODO(%S)",
    "load ${type.kotlinApi}",
  )

  override fun BridgeBuilder.store(
    baseAddress: CodeBlock,
    offset: Int,
    value: CodeBlock,
  ) {
    code.addStatement("// TODO: store ${type.kotlinApi}")
  }

  override fun FlatEncoder.liftFlat() {
    take()
    put("TODO(%S)", "lift ${type.kotlinApi}")
  }

  override fun FlatEncoder.lowerFlat() {
    take()
    put("TODO(%S)", "lower ${coreType.kotlinCoreType}")
  }
}

open class DirectEncoder(
  override val alignment: Int,
  val coreType: CoreType,
) : Encoder() {
  override val coreTypes: List<CoreType>
    get() = listOf(coreType)

  override val byteCount: Int
    get() = coreType.byteCount

  override fun BridgeBuilder.load(
    baseAddress: CodeBlock,
    offset: Int,
  ): CodeBlock {
    return coreTypeToValue(platform.load(baseAddress, offset, coreType))
  }

  override fun BridgeBuilder.store(
    baseAddress: CodeBlock,
    offset: Int,
    value: CodeBlock,
  ) {
    platform.store(baseAddress, offset, coreType, valueToCoreType(value))
  }

  override fun FlatEncoder.liftFlat() {
    put(coreTypeToValue(take()))
  }

  override fun FlatEncoder.lowerFlat() {
    put(valueToCoreType(take()))
  }

  open fun coreTypeToValue(coreType: CodeBlock): CodeBlock = coreType

  open fun valueToCoreType(value: CodeBlock): CodeBlock = value
}

object BooleanEncoder : DirectEncoder(
  alignment = 1,
  coreType = CoreType.I32,
) {
  override fun coreTypeToValue(coreType: CodeBlock) =
    CodeBlock.of("(%L != 0)", coreType)

  override fun valueToCoreType(value: CodeBlock) =
    CodeBlock.of("(if (%L) 1 else 0)", value)
}

object ByteEncoder : DirectEncoder(
  alignment = 1,
  coreType = CoreType.I32,
)

object ShortEncoder : DirectEncoder(
  alignment = 2,
  coreType = CoreType.I32,
)

object IntEncoder : DirectEncoder(
  alignment = 4,
  coreType = CoreType.I32,
)

object LongEncoder : DirectEncoder(
  alignment = 8,
  coreType = CoreType.I64,
)

object UByteEncoder : DirectEncoder(
  alignment = 1,
  coreType = CoreType.I32,
) {
  override fun coreTypeToValue(coreType: CodeBlock) =
    CodeBlock.of("%L.toUByte()", coreType)

  override fun valueToCoreType(value: CodeBlock) =
    CodeBlock.of("%L.toInt()", value)
}

object UShortEncoder : DirectEncoder(
  alignment = 2,
  coreType = CoreType.I32,
) {
  override fun coreTypeToValue(coreType: CodeBlock) =
    CodeBlock.of("%L.toUShort()", coreType)

  override fun valueToCoreType(value: CodeBlock) =
    CodeBlock.of("%L.toInt()", value)
}

object UIntEncoder : DirectEncoder(
  alignment = 4,
  coreType = CoreType.I32,
) {
  override fun coreTypeToValue(coreType: CodeBlock) =
    CodeBlock.of("%L.toUInt()", coreType)

  override fun valueToCoreType(value: CodeBlock) =
    CodeBlock.of("%L.toInt()", value)
}

object ULongEncoder : DirectEncoder(
  alignment = 4,
  coreType = CoreType.I64,
) {
  override fun coreTypeToValue(coreType: CodeBlock) =
    CodeBlock.of("%L.toULong()", coreType)

  override fun valueToCoreType(value: CodeBlock) =
    CodeBlock.of("%L.toLong()", value)
}

object FloatEncoder : DirectEncoder(
  alignment = 4,
  coreType = CoreType.F32,
)

object DoubleEncoder : DirectEncoder(
  alignment = 8,
  coreType = CoreType.F64,
)

object CharEncoder : DirectEncoder(
  alignment = 4,
  coreType = CoreType.I32,
)

class ResourceEncoder(
  private val type: TypeName.Declared,
) : Encoder() {
  override val coreTypes = listOf(CoreType.I32)

  override val byteCount: Int
    get() = CoreType.I32.byteCount

  override val alignment: Int
    get() = CoreType.I32.alignment

  override fun BridgeBuilder.load(
    baseAddress: CodeBlock,
    offset: Int,
  ) = CodeBlock.of("TODO(%S)", "ResourceEncoder")

  override fun BridgeBuilder.store(
    baseAddress: CodeBlock,
    offset: Int,
    value: CodeBlock,
  ) {
    code.addStatement("// TODO: ResourceEncoder")
  }

  override fun FlatEncoder.liftFlat() {
    put(platform.liftResource(take(), type))
  }

  override fun FlatEncoder.lowerFlat() {
    put(platform.lowerResource(take(), type))
  }
}

/** Stores a string as an address pointer and a byte count. */
object StringEncoder : Encoder() {
  override val coreTypes: List<CoreType>
    get() = listOf(CoreType.Pointer, CoreType.I32)

  override val nameHints: List<Identifier>
    get() = listOf(Identifier("pointer"), Identifier("byte-count"))

  override val byteCount: Int
    get() = 8

  override val alignment: Int
    get() = CoreType.Pointer.alignment

  override fun BridgeBuilder.load(
    baseAddress: CodeBlock,
    offset: Int,
  ): CodeBlock {
    val address = nameAllocator.newName("stringAddress")
    val byteCount = nameAllocator.newName("stringByteCount")
    code.addStatement(
      "val %N = %L",
      address,
      platform.load(baseAddress, offset, CoreType.Pointer),
    )
    code.addStatement(
      "val %N = %L",
      byteCount,
      platform.load(baseAddress, offset + 4, CoreType.I32),
    )
    return platform.loadString(
      CodeBlock.of("%N", address),
      CodeBlock.of("%N", byteCount),
    )
  }

  override fun BridgeBuilder.store(
    baseAddress: CodeBlock,
    offset: Int,
    value: CodeBlock,
  ) {
    val (addressCodeBlock, byteCountCodeBlock) = platform.storeString(value)
    val address = nameAllocator.newName("stringAddress")
    val byteCount = nameAllocator.newName("stringByteCount")
    code.addStatement(
      "val %N = %L",
      address,
      addressCodeBlock,
    )
    code.addStatement(
      "val %N = %L",
      byteCount,
      byteCountCodeBlock,
    )
    platform.store(baseAddress, offset, CoreType.Pointer, CodeBlock.of("%N", address))
    platform.store(baseAddress, offset + 4, CoreType.I32, CodeBlock.of("%N", byteCount))
  }

  override fun FlatEncoder.liftFlat() {
    put(platform.loadString(take(), take()))
  }

  override fun FlatEncoder.lowerFlat() {
    val (address, size) = platform.storeString(take())
    put(address)
    put(size)
  }
}

/** Returns the smallest int greater or equal to this, and that equally divides [alignment]. */
fun Int.alignTo(alignment: Int): Int =
  ((this + alignment - 1) / alignment) * alignment
