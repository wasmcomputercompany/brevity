package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock
import dev.wasmo.brevity.ir.IrVariant
import dev.wasmo.brevity.kotlin.generator.kotlinApi
import dev.wasmo.brevity.kotlin.generator.kotlinName

abstract class AbstractVariantEncoder(
  protected val cases: List<Encoder?>,
) : Encoder() {
  private val discriminant = IntegerType.discriminant(cases.size)

  private val maxCaseAlignment: Int =
    cases.maxOfOrNull { it?.alignment ?: 1 } ?: 1

  override val alignment: Int = maxOf(maxCaseAlignment, discriminant.byteCount)

  override val byteCount: Int = run {
    val s = discriminant.byteCount.alignTo(maxCaseAlignment)
    val cs = cases.maxOfOrNull { it?.byteCount ?: 0 } ?: 0
    (s + cs).alignTo(alignment)
  }

  private val casesCoreTypesBits: List<CoreType> =
    cases.mapNotNull { it?.coreTypes }.bitwiseUnion()

  override val coreTypes = listOf(CoreType.I32) + casesCoreTypesBits

  /** Turns an index and argument into an instance. */
  abstract fun constructInstance(index: Int, value: CodeBlock?): CodeBlock

  /** Returns a code block that is true if [candidate] matches [index]. */
  abstract fun matchInstance(index: Int, candidate: CodeBlock): CodeBlock

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
    for ((index, case) in cases.withIndex()) {
      code.beginControlFlow("%L ->", index)
      code.addStatement(
        "%L",
        constructInstance(
          index = index,
          value = case?.let {
            with(case) {
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
    code.beginControlFlow("when")
    for ((index, case) in cases.withIndex()) {
      code.beginControlFlow("%L ->", matchInstance(index, value))
      platform.store(baseAddress, offset, discriminant, CodeBlock.of("%L", index))

      if (case != null) {
        with(case) {
          store(
            baseAddress = baseAddress,
            offset = offset + discriminant.byteCount.alignTo(maxCaseAlignment),
            value = instanceValue(index, value)
              ?: error("case mismatch for $index"),
          )
        }
      }
      code.endControlFlow()
    }
    code.addStatement("else -> error(%S)", "unexpected case")
    code.endControlFlow()
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
    for ((caseIndex, case) in cases.withIndex()) {
      val value = case?.let {
        liftFlat(
          values = case.coreTypes.withIndex().map { (v, requiredType) ->
            requiredType.fromBits(
              sourceType = casesCoreTypesBits[v],
              value = caseCoreValuesBits[v],
            )
          },
          encoder = case,
        )
      }
      code.addStatement(
        "%L -> %L",
        caseIndex,
        constructInstance(index = caseIndex, value = value),
      )
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
    code.beginControlFlow("val %N = when", discriminatorName)
    for ((caseIndex, case) in cases.withIndex()) {
      code.beginControlFlow("%L ->", matchInstance(caseIndex, variant))
      if (case != null) {
        val values = lowerFlat(instanceValue(caseIndex, variant)!!, case)
        for ((v, coreType) in case.coreTypes.withIndex()) {
          code.addStatement(
            "%N = %L",
            caseCoreValuesBitsNames[v],
            coreType.fromBits(casesCoreTypesBits[v], values[v])
          )
        }
      }
      code.addStatement("%L", caseIndex)
      code.endControlFlow()
    }
    code.addStatement("else -> error(%S)", "unexpected case")
    code.endControlFlow()

    put("%N", discriminatorName)
    for (caseCoreValueBits in caseCoreValuesBitsNames) {
      put("%N", caseCoreValueBits)
    }
  }
}

class VariantEncoder(
  private val type: IrVariant,
  cases: List<Encoder?>,
) : AbstractVariantEncoder(cases) {
  override fun constructInstance(index: Int, value: CodeBlock?): CodeBlock {
    val case = type.cases[index]
    val enclosingName = type.type.kotlinApi.nestedClass(case.kotlinName)
    return when {
      case.type != null -> CodeBlock.of("%T(%L)", enclosingName, value!!)
      else -> CodeBlock.of("%T", enclosingName)
    }
  }

  override fun matchInstance(index: Int, candidate: CodeBlock): CodeBlock {
    val case = type.cases[index]
    return CodeBlock.of(
      "%L is %T",
      candidate,
      type.type.kotlinApi.nestedClass(case.kotlinName),
    )
  }

  override fun instanceValue(
    index: Int,
    value: CodeBlock,
  ): CodeBlock? {
    val case = type.cases[index]
    if (case.type == null) return null
    return CodeBlock.of("%L.value", value)
  }
}
