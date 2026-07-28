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

  open val nameHints: List<Identifier>?
    get() = null

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

  override fun FlatEncoder.liftFlat() {
    put("(%L as %T)", take(), type.kotlinApi)
  }

  override fun FlatEncoder.lowerFlat() {
    put("(%L as %T)", take(), coreType.kotlinCoreType)
  }
}

class TupleEncoder(
  private val encoders: List<Encoder>,
) : Encoder() {
  override val coreTypes = encoders.flatMap { it.coreTypes }

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

  override fun FlatEncoder.liftFlat() {
    put(platform.loadString(take(), take()))
  }

  override fun FlatEncoder.lowerFlat() {
    val (address, size) = platform.storeString(take())
    put(address)
    put(size)
  }
}
