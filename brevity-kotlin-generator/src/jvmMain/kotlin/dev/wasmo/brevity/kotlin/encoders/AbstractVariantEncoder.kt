package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.UNIT
import dev.wasmo.brevity.ir.IrCase
import dev.wasmo.brevity.kotlin.generator.Symbols
import dev.wasmo.brevity.kotlin.generator.kotlinName

/**
 * Implement the structure of variant encoding.
 *
 * Each element has a 1, 2, or 4-byte discriminator case index, followed by an optional
 * case-specific value.
 *
 * When flattening, this does a bitwise union of all cases' case-specific value into a shared set
 * of parameters.
 *
 * Subclasses select and construct the specific case values.
 */
abstract class AbstractVariantEncoder(
  protected val caseEncoders: List<Encoder?>,
) : Encoder() {
  private val discriminant = IntegerType.discriminant(caseEncoders.size)

  private val maxCaseAlignment: Int =
    caseEncoders.maxOfOrNull { it?.alignment ?: 1 } ?: 1

  override val alignment: Int = maxOf(maxCaseAlignment, discriminant.byteCount)

  override val byteCount: Int = run {
    val s = discriminant.byteCount.alignTo(maxCaseAlignment)
    val cs = caseEncoders.maxOfOrNull { it?.byteCount ?: 0 } ?: 0
    (s + cs).alignTo(alignment)
  }

  private val casesCoreTypesBits: List<CoreType> =
    caseEncoders.mapNotNull { it?.coreTypes }.bitwiseUnion()

  override val coreTypes = listOf(CoreType.I32) + casesCoreTypesBits

  /** Turns an index and argument into an instance. */
  abstract fun constructInstance(index: Int, value: CodeBlock?): CodeBlock

  /** Returns a when case that matches [index]. */
  abstract fun matchInstance(index: Int): CodeBlock

  /** Returns a code block that extracts the value of [index]. */
  abstract fun instanceValue(index: Int, value: CodeBlock): CodeBlock?

  override fun BridgeBuilder.load(
    baseAddress: CodeBlock,
    offset: Int,
  ): CodeBlock {
    val variantName = nameAllocator.newName("variant")

    code.beginControlFlow(
      "val %N = when (%L.toInt()) {",
      variantName,
      platform.load(baseAddress, offset, discriminant),
    )
    for ((index, caseEncoder) in caseEncoders.withIndex()) {
      code.beginControlFlow("%L ->", index)
      code.addStatement(
        "%L",
        constructInstance(
          index = index,
          value = caseEncoder?.let {
            with(caseEncoder) {
              load(
                baseAddress = baseAddress,
                offset = offset + discriminant.byteCount.alignTo(maxCaseAlignment),
              )
            }
          },
        ),
      )
      code.endControlFlow()
    }
    code.addStatement("else -> error(%S)", "unexpected case")
    code.endControlFlow()

    return CodeBlock.of("%N", variantName)
  }

  override fun BridgeBuilder.store(
    baseAddress: CodeBlock,
    offset: Int,
    value: CodeBlock,
  ) {
    val variantName = nameAllocator.newName("variant")
    val discriminatorName = nameAllocator.newName("discriminator")
    code.beginControlFlow(
      "val %N: %T = when (val %N = %L)",
      discriminatorName,
      discriminant.kotlinType,
      variantName,
      value,
    )
    for ((index, caseEncoder) in caseEncoders.withIndex()) {
      code.beginControlFlow("%L ->", matchInstance(index))
      if (caseEncoder != null) {
        with(caseEncoder) {
          store(
            baseAddress = baseAddress,
            offset = offset + discriminant.byteCount.alignTo(maxCaseAlignment),
            value = instanceValue(index, CodeBlock.of("%N", variantName))
              ?: error("case mismatch for $index"),
          )
        }
      }
      code.addStatement("%L", index)
      code.endControlFlow()
    }
    code.endControlFlow()
    platform.store(baseAddress, offset, discriminant, CodeBlock.of("%N", discriminatorName))
  }

  override fun FlatEncoder.liftFlat() {
    val variantName = nameAllocator.newName("variant")

    val discriminator = take()
    val caseCoreValuesBits = casesCoreTypesBits.map { take() }

    code.beginControlFlow(
      "val %L = when (%L)",
      variantName,
      discriminator,
    )
    for ((caseIndex, caseEncoder) in caseEncoders.withIndex()) {
      code.beginControlFlow("%L ->", caseIndex)
      code.addStatement(
        "%L",
        constructInstance(
          index = caseIndex,
          value = caseEncoder?.let {
            liftFlat(
              values = caseEncoder.coreTypes.withIndex().map { (v, requiredType) ->
                requiredType.fromBits(
                  sourceType = casesCoreTypesBits[v],
                  value = caseCoreValuesBits[v],
                )
              },
              encoder = caseEncoder,
            )
          },
        ),
      )
      code.endControlFlow()
    }
    code.addStatement("else -> error(%S)", "unexpected case")
    code.endControlFlow()

    return put(variantName)
  }

  override fun FlatEncoder.lowerFlat() {
    val variantName = nameAllocator.newName("variant")
    code.addStatement("val %N = %L", variantName, take())
    val variant = CodeBlock.of("%N", variantName)

    val caseCoreValuesBitsNames = casesCoreTypesBits.withIndex().map { (index, type) ->
      val name = nameAllocator.newName("coreValueBits$index")
      code.addStatement("var %N = %L", name, type.zero)
      name
    }

    val discriminatorName = nameAllocator.newName("discriminator")
    code.beginControlFlow(
      "val %N = when (%N)",
      discriminatorName,
      variantName,
    )
    for ((caseIndex, caseEncoder) in caseEncoders.withIndex()) {
      code.beginControlFlow("%L ->", matchInstance(caseIndex))
      if (caseEncoder != null) {
        val values = lowerFlat(instanceValue(caseIndex, variant)!!, caseEncoder)
        for ((v, coreType) in caseEncoder.coreTypes.withIndex()) {
          code.addStatement(
            "%N = %L",
            caseCoreValuesBitsNames[v],
            coreType.fromBits(casesCoreTypesBits[v], values[v]),
          )
        }
      }
      code.addStatement("%L", caseIndex)
      code.endControlFlow()
    }
    code.endControlFlow()

    put("%N", discriminatorName)
    for (caseCoreValueBits in caseCoreValuesBitsNames) {
      put("%N", caseCoreValueBits)
    }
  }
}

class VariantEncoder(
  private val kotlinType: ClassName,
  private val cases: List<IrCase>,
  caseEncoders: List<Encoder?>,
) : AbstractVariantEncoder(caseEncoders) {
  override fun constructInstance(index: Int, value: CodeBlock?): CodeBlock {
    val case = cases[index]
    return when {
      case.type != null -> CodeBlock.of(
        "%T(%L)",
        kotlinType.nestedClass(case.kotlinName),
        value!!,
      )

      else -> CodeBlock.of(
        "%T.%N",
        kotlinType,
        case.kotlinName,
      )
    }
  }

  override fun matchInstance(index: Int): CodeBlock {
    val case = cases[index]
    return when {
      case.type != null -> CodeBlock.of(
        "is %T",
        kotlinType.nestedClass(case.kotlinName),
      )

      else -> CodeBlock.of(
        "%T.%N",
        kotlinType,
        case.kotlinName,
      )
    }
  }

  override fun instanceValue(
    index: Int,
    value: CodeBlock,
  ): CodeBlock? {
    val case = cases[index]
    if (case.type == null) return null
    return CodeBlock.of("%L.value", value)
  }
}

class EnumEncoder(
  private val kotlinType: ClassName,
  private val cases: List<IrCase>,
) : AbstractVariantEncoder(cases.map { null }) {
  override fun constructInstance(index: Int, value: CodeBlock?) =
    CodeBlock.of("%T.%N", kotlinType, cases[index].kotlinName)

  override fun matchInstance(index: Int) =
    CodeBlock.of("%T.%N", kotlinType, cases[index].kotlinName)

  override fun instanceValue(index: Int, value: CodeBlock): CodeBlock? = null
}

class OptionalEncoder(
  some: Encoder,
) : AbstractVariantEncoder(listOf(null, some)) {
  override fun constructInstance(
    index: Int,
    value: CodeBlock?,
  ): CodeBlock {
    return when (index) {
      0 -> CodeBlock.of("null")
      else -> value!!
    }
  }

  override fun matchInstance(index: Int): CodeBlock {
    return when (index) {
      0 -> CodeBlock.of("null")
      else -> CodeBlock.of("else")
    }
  }

  override fun instanceValue(
    index: Int,
    value: CodeBlock,
  ): CodeBlock? {
    return when (index) {
      0 -> null
      else -> value
    }
  }
}

class ResultEncoder(
  private val ok: Pair<TypeName, Encoder>?,
  private val error: Pair<TypeName, Encoder>?,
) : AbstractVariantEncoder(listOf(ok?.second, error?.second)) {
  override fun constructInstance(index: Int, value: CodeBlock?) =
    CodeBlock.of(
      "%T<%T, %T>(%L)",
      rawType(index),
      ok?.first ?: UNIT,
      error?.first ?: UNIT,
      value ?: UNIT,
    )

  override fun matchInstance(index: Int) =
    CodeBlock.of("is %T", rawType(index))

  override fun instanceValue(index: Int, value: CodeBlock) =
    CodeBlock.of("%L.value", value)

  private fun rawType(index: Int): TypeName {
    return when (index) {
      0 -> Symbols.Brevity.ResultOk
      1 -> Symbols.Brevity.ResultError
      else -> error("unexpected discriminator: $index")
    }
  }
}
