package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.UNIT
import dev.wasmo.brevity.ir.IrFunction
import dev.wasmo.brevity.kotlin.KotlinMapper

internal class ApiFunctionFactory(
  private val kotlinMapper: KotlinMapper,
  private val function: BridgeFunction,
  private val supportAsync: Boolean,
) {
  private val value: IrFunction
    get() = function.function

  private val nameAllocator: NameAllocator
    get() = function.nameAllocator

  fun api() = FunSpec.builder(function.kotlinName)
    .addModifiers(KModifier.ABSTRACT)
    .apply {
      if (value.async && supportAsync) {
        addModifiers(KModifier.SUSPEND)
      }
      val kdoc = buildString {
        val functionDocumentation = value.documentation
        if (functionDocumentation != null) {
          append(functionDocumentation.content.trimIndent())
          append("\n\n")
        }

        for (parameter in value.parameters) {
          val parameterDocumentation = parameter.documentation ?: continue
          val parameterName = nameAllocator[parameter.name]
          append("@param $parameterName ")
          append(parameterDocumentation.content.trimIndent().replace("\n", "\n  "))
          append("\n\n")
        }
      }.trim()
      if (kdoc.isNotEmpty()) {
        addKdoc("%L", kdoc)
      }

      for (parameter in value.parameters) {
        addParameter(nameAllocator[parameter.name], kotlinMapper.get(parameter.type))
      }

      returns(value.returnType?.let { kotlinMapper.get(it) } ?: UNIT)
    }
    .build()
}
