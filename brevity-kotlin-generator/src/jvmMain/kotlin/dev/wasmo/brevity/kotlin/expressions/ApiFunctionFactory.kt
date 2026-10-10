package dev.wasmo.brevity.kotlin.expressions

import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier

/** The Kotlin public API of a WIT function. */
class ApiFunctionFactory {
  fun create(abiFunction: AbiFunction) = builder(abiFunction)
    .addModifiers(KModifier.ABSTRACT)
    .apply {
      val documentation = abiFunction.documentation
      if (documentation != null) {
        addKdoc(documentation.trimIndent())
      }
    }
    .build()

  fun builder(abiFunction: AbiFunction): FunSpec.Builder {
    return FunSpec.builder(abiFunction.kotlinName)
      .apply {
        // TODO: restore support for async.
        if (false && abiFunction.async) {
          addModifiers(KModifier.SUSPEND)
        }

        addParameters(abiFunction.parameters.liftedSpecs)
        val resultValue = abiFunction.result.value
        if (resultValue != null) {
          returns(resultValue.type)
        }
      }
  }
}
