package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.UNIT
import dev.wasmo.brevity.ir.IrFunction

internal class ApiFunctionFactory(
  private val value: IrFunction,
) {
  private val nameAllocator = NameAllocator().apply {
    // Pre-allocate all the names we'll need.
    for (parameter in value.parameters) {
      newName(parameter.kotlinName, parameter.name)
    }
  }

  fun api() = FunSpec.builder(value.kotlinName)
    .addModifiers(KModifier.ABSTRACT)
    .apply {
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
        addParameter(nameAllocator[parameter.name], parameter.type.kotlinApi)
      }

      returns(value.returnType?.kotlinApi ?: UNIT)
    }
    .build()
}
