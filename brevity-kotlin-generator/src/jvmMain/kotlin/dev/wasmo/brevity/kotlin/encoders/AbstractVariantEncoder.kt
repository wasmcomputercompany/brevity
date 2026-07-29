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

  override val coreTypes: List<CoreType> =
    listOf(CoreType.I32) + cases.mapNotNull { it?.coreTypes }.bitwiseUnion()

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
    code.endControlFlow()
  }

  override fun FlatEncoder.liftFlat() {
    for (coreType in coreTypes) {
      take()
    }
    put("TODO(%S)", "lift variant")
  }

  override fun FlatEncoder.lowerFlat() {
    take()
    for (coreType in coreTypes) {
      put("TODO(%S)", "lower variant")
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
