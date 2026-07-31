package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.UNIT
import dev.wasmo.brevity.ir.IrCase
import dev.wasmo.brevity.kotlin.code.CodeBuilder
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

  context(codeBuilder: CodeBuilder)
  override fun load(
    baseAddress: CodeBlock,
    offset: Int,
  ): CodeBlock {
    val variantName = codeBuilder.newName("variant")

    codeBuilder.beginControlFlow(
      "val %N = when (%L.toInt()) {",
      variantName,
      codeBuilder.platform.load(baseAddress, offset, discriminant),
    )
    for ((index, caseEncoder) in caseEncoders.withIndex()) {
      codeBuilder.beginControlFlow("%L ->", index)
      codeBuilder.addStatement(
        "%L",
        constructInstance(
          index = index,
          value = caseEncoder?.let {
            caseEncoder.load(
              baseAddress = baseAddress,
              offset = offset + discriminant.byteCount.alignTo(maxCaseAlignment),
            )
          },
        ),
      )
      codeBuilder.endControlFlow()
    }
    codeBuilder.addStatement("else -> error(%S)", "unexpected case")
    codeBuilder.endControlFlow()

    return CodeBlock.of("%N", variantName)
  }

  context(codeBuilder: CodeBuilder)
  override fun store(
    baseAddress: CodeBlock,
    offset: Int,
    value: CodeBlock,
  ) {
    val variantName = codeBuilder.newName("variant")
    val discriminatorName = codeBuilder.newName("discriminator")
    codeBuilder.beginControlFlow(
      "val %N: %T = when (val %N = %L)",
      discriminatorName,
      discriminant.kotlinType,
      variantName,
      value,
    )
    for ((index, caseEncoder) in caseEncoders.withIndex()) {
      codeBuilder.beginControlFlow("%L ->", matchInstance(index))
      caseEncoder?.store(
        baseAddress = baseAddress,
        offset = offset + discriminant.byteCount.alignTo(maxCaseAlignment),
        value = instanceValue(index, CodeBlock.of("%N", variantName))
              ?: error("case mismatch for $index"),
      )
      codeBuilder.addStatement("%L", index)
      codeBuilder.endControlFlow()
    }
    codeBuilder.endControlFlow()
    codeBuilder.platform.store(
      baseAddress,
      offset,
      discriminant,
      CodeBlock.of("%N", discriminatorName),
    )
  }

  context(codeBuilder: CodeBuilder)
  override fun liftFlat(flatBuilder: FlatBuilder) {
    val variantName = codeBuilder.newName("variant")

    val discriminator = flatBuilder.take()
    val caseCoreValuesBits = casesCoreTypesBits.map { flatBuilder.take() }

    codeBuilder.beginControlFlow(
      "val %L = when (%L)",
      variantName,
      discriminator,
    )
    for ((caseIndex, caseEncoder) in caseEncoders.withIndex()) {
      codeBuilder.beginControlFlow("%L ->", caseIndex)
      codeBuilder.addStatement(
        "%L",
        constructInstance(
          index = caseIndex,
          value = caseEncoder?.let {
            caseEncoder.liftFlat(
              values = caseEncoder.coreTypes.withIndex().map { (v, requiredType) ->
                requiredType.fromBits(
                  sourceType = casesCoreTypesBits[v],
                  value = caseCoreValuesBits[v],
                )
              },
            )
          },
        ),
      )
      codeBuilder.endControlFlow()
    }
    codeBuilder.addStatement("else -> error(%S)", "unexpected case")
    codeBuilder.endControlFlow()

    return flatBuilder.put(variantName)
  }

  context(codeBuilder: CodeBuilder)
  override fun lowerFlat(flatBuilder: FlatBuilder) {
    val variantName = codeBuilder.newName("variant")
    codeBuilder.addStatement("val %N = %L", variantName, flatBuilder.take())
    val variant = CodeBlock.of("%N", variantName)

    val caseCoreValuesBitsNames = casesCoreTypesBits.withIndex().map { (index, type) ->
      val name = codeBuilder.newName("coreValueBits$index")
      codeBuilder.addStatement("var %N = %L", name, type.zero)
      name
    }

    val discriminatorName = codeBuilder.newName("discriminator")
    codeBuilder.beginControlFlow(
      "val %N = when (%N)",
      discriminatorName,
      variantName,
    )
    for ((caseIndex, caseEncoder) in caseEncoders.withIndex()) {
      codeBuilder.beginControlFlow("%L ->", matchInstance(caseIndex))
      if (caseEncoder != null) {
        val values = caseEncoder.lowerFlat(instanceValue(caseIndex, variant)!!)
        for ((v, coreType) in caseEncoder.coreTypes.withIndex()) {
          codeBuilder.addStatement(
            "%N = %L",
            caseCoreValuesBitsNames[v],
            coreType.toBits(casesCoreTypesBits[v], values[v]),
          )
        }
      }
      codeBuilder.addStatement("%L", caseIndex)
      codeBuilder.endControlFlow()
    }
    codeBuilder.endControlFlow()

    flatBuilder.put("%N", discriminatorName)
    for (caseCoreValueBits in caseCoreValuesBitsNames) {
      flatBuilder.put("%N", caseCoreValueBits)
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
