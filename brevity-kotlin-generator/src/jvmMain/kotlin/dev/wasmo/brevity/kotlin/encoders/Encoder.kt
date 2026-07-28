package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.buildCodeBlock
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.kotlin.generator.Symbols
import dev.wasmo.brevity.kotlin.generator.kotlinApi
import dev.wasmo.brevity.kotlin.generator.kotlinCoreType

abstract class Encoder {
  abstract val coreTypes: List<CoreType>
  abstract val byteCount: Int

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

  override fun BridgeBuilder.load(
    baseAddress: CodeBlock,
    offset: Int,
  ) = CodeBlock.of(
    "(%L as %T)",
    platform.load(baseAddress, offset, coreType),
    type.kotlinApi,
  )

  override fun BridgeBuilder.store(
    baseAddress: CodeBlock,
    offset: Int,
    value: CodeBlock,
  ) {
    platform.store(
      baseAddress,
      offset,
      coreType,
      CodeBlock.of("(%L as %T)", value, coreType.kotlinCoreType),
    )
  }

  override fun FlatEncoder.liftFlat() {
    put("(%L as %T)", take(), type.kotlinApi)
  }

  override fun FlatEncoder.lowerFlat() {
    put("(%L as %T)", take(), coreType.kotlinCoreType)
  }
}

open class DirectEncoder(
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
    return platform.load(baseAddress, offset, coreType)
  }

  override fun BridgeBuilder.store(
    baseAddress: CodeBlock,
    offset: Int,
    value: CodeBlock,
  ) {
    platform.store(baseAddress, offset, coreType, value)
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

object BooleanEncoder : DirectEncoder(CoreType.I32) {
  override fun coreTypeToValue(coreType: CodeBlock) =
    CodeBlock.of("%L != 0", coreType)

  override fun valueToCoreType(value: CodeBlock) =
    CodeBlock.of("if (%L) 1 else 0", value)
}

object ByteEncoder : DirectEncoder(CoreType.I32)
object ShortEncoder : DirectEncoder(CoreType.I32)
object IntEncoder : DirectEncoder(CoreType.I32)
object LongEncoder : DirectEncoder(CoreType.I64)

object UByteEncoder : DirectEncoder(CoreType.I32) {
  override fun coreTypeToValue(coreType: CodeBlock) =
    CodeBlock.of("%L.toUByte()", coreType)

  override fun valueToCoreType(value: CodeBlock) =
    CodeBlock.of("%L.toInt()", value)
}

object UShortEncoder : DirectEncoder(CoreType.I32) {
  override fun coreTypeToValue(coreType: CodeBlock) =
    CodeBlock.of("%L.toUShort()", coreType)

  override fun valueToCoreType(value: CodeBlock) =
    CodeBlock.of("%L.toInt()", value)
}

object UIntEncoder : DirectEncoder(CoreType.I32) {
  override fun coreTypeToValue(coreType: CodeBlock) =
    CodeBlock.of("%L.toUInt()", coreType)

  override fun valueToCoreType(value: CodeBlock) =
    CodeBlock.of("%L.toInt()", value)
}

object ULongEncoder : DirectEncoder(CoreType.I64) {
  override fun coreTypeToValue(coreType: CodeBlock) =
    CodeBlock.of("%L.toULong()", coreType)

  override fun valueToCoreType(value: CodeBlock) =
    CodeBlock.of("%L.toLong()", value)
}

object FloatEncoder : DirectEncoder(CoreType.F32)

object DoubleEncoder : DirectEncoder(CoreType.F64)

object CharEncoder : DirectEncoder(CoreType.I32)

class TupleEncoder(
  private val encoders: List<Encoder>,
) : Encoder() {
  override val coreTypes = encoders.flatMap { it.coreTypes }

  override val byteCount: Int
    get() = encoders.sumOf { it.byteCount }

  override fun BridgeBuilder.load(
    baseAddress: CodeBlock,
    offset: Int,
  ) = CodeBlock.of("TODO(%S)", "TupleEncoder")

  override fun BridgeBuilder.store(
    baseAddress: CodeBlock,
    offset: Int,
    value: CodeBlock,
  ) {
    code.addStatement("// TODO: TupleEncoder")
  }

  override fun FlatEncoder.liftFlat() {
    val elements = encoders.map { encoder ->
      liftFlat(
        values = encoder.coreTypes.map { take() },
        encoder = encoder,
      )
    }

    when (elements.size) {
      2 -> put(
        "(%L to %L)",
        elements[0],
        elements[1],
      )

      3 -> put(
        "%T(%L, %L, %L)",
        Symbols.Kotlin.Triple,
        elements[0],
        elements[1],
        elements[2],
      )

      4 -> put(
        "%T(%L, %L, %L, %L)",
        Symbols.Brevity.Quad,
        elements[0],
        elements[1],
        elements[2],
        elements[3],
      )

      else -> put(
        buildCodeBlock {
          add("%M(⇥\n", Symbols.KotlinCollections.ListOf)
          for (element in elements) {
            add("%L,\n", element)
          }
          add("⇤)", Symbols.KotlinCollections.ListOf)
        },
      )
    }
  }

  override fun FlatEncoder.lowerFlat() {
    val tuple = nameAllocator.newName("tuple")
    code.addStatement("val %N = %L", tuple, take())

    val elements = when (encoders.size) {
      2 -> listOf(
        CodeBlock.of("%N.first", tuple),
        CodeBlock.of("%N.second", tuple),
      )

      3 -> listOf(
        CodeBlock.of("%N.first", tuple),
        CodeBlock.of("%N.second", tuple),
        CodeBlock.of("%N.third", tuple),
      )

      4 -> listOf(
        CodeBlock.of("%N.a", tuple),
        CodeBlock.of("%N.b", tuple),
        CodeBlock.of("%N.c", tuple),
        CodeBlock.of("%N.d", tuple),
      )

      else -> encoders.withIndex().map { (i, _) ->
        CodeBlock.of("%N[%L]", tuple, i)
      }
    }

    for ((i, encoder) in encoders.withIndex()) {
      for (coreType in lowerFlat(elements[i], encoder)) {
        put(coreType)
      }
    }
  }
}

class ListEncoder(
  private val type: TypeName,
) : Encoder() {
  override val coreTypes = listOf(CoreType.Pointer)

  override val byteCount: Int
    get() = CoreType.Pointer.byteCount

  override fun BridgeBuilder.load(
    baseAddress: CodeBlock,
    offset: Int,
  ) = CodeBlock.of("TODO(%S)", "ListEncoder")

  override fun BridgeBuilder.store(
    baseAddress: CodeBlock,
    offset: Int,
    value: CodeBlock,
  ) {
    code.addStatement("// TODO: ListEncoder")
  }

  override fun FlatEncoder.liftFlat() {
    put("(%L as %T)", take(), type.kotlinApi)
  }

  override fun FlatEncoder.lowerFlat() {
    put("(%L as %T)", take(), CoreType.Pointer.kotlinCoreType)
  }
}

class ResourceEncoder(
  private val type: TypeName.Declared,
) : Encoder() {
  override val coreTypes = listOf(CoreType.I32)

  override val byteCount: Int
    get() = CoreType.I32.byteCount

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
