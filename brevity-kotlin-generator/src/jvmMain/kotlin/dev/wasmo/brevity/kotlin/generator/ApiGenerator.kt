package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.Documentable
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import dev.wasmo.brevity.FunctionName
import dev.wasmo.brevity.ir.IrDeclaration
import dev.wasmo.brevity.ir.IrEnum
import dev.wasmo.brevity.ir.IrExternalApi
import dev.wasmo.brevity.ir.IrFlags
import dev.wasmo.brevity.ir.IrFunction
import dev.wasmo.brevity.ir.IrInterface
import dev.wasmo.brevity.ir.IrRecord
import dev.wasmo.brevity.ir.IrResource
import dev.wasmo.brevity.ir.IrTypeAlias
import dev.wasmo.brevity.ir.IrVariant
import dev.wasmo.brevity.ir.IrWitPackage
import dev.wasmo.brevity.ir.IrWorld

class ApiGenerator(
  private val packages: List<IrWitPackage>,
) {
  fun generate(): List<QualifiedSpec> {
    val result = mutableListOf<QualifiedSpec>()

    for (service in packages.flatMap { it.services }) {
      val serviceName = service.serviceName.kotlinApi
      result.collect(
        sourceSet = QualifiedSpec.SourceSet.CommonMain,
        locations = setOf(service.location),
        packageName = serviceName.packageName,
        fileName = serviceName.simpleName,
      ) {
        generateApi(service)
      }
    }

    return result
  }

  private fun <T : Documentable.Builder<*>> T.setDeclaration(
    declaration: IrDeclaration? = null,
  ): T = apply {
    val documentation = declaration?.documentation
    if (documentation != null) {
      addKdoc(documentation.content.trimIndent())
    }
  }

  context(collector: QualifiedSpecCollector)
  private fun generateRecord(value: IrRecord) {
    collector.addType(
      className = value.type.kotlinApi,
      type = TypeSpec.classBuilder(value.type.kotlinApi)
        .addModifiers(KModifier.DATA)
        .setDeclaration(value)
        .apply {
          val constructorBuilder = FunSpec.constructorBuilder()

          for (field in value.fields) {
            val name = field.kotlinName
            val parameter = ParameterSpec.builder(name, field.type.kotlinApi)
              .build()
            constructorBuilder.addParameter(parameter)

            addProperty(
              PropertySpec.builder(name, field.type.kotlinApi)
                .initializer("%N", parameter)
                .setDeclaration(field)
                .build(),
            )
          }

          primaryConstructor(constructorBuilder.build())
        }
        .build(),
    )
  }

  context(collector: QualifiedSpecCollector)
  private fun generateResource(value: IrResource) {
    collector.addType(
      className = value.type.kotlinApi,
      type = TypeSpec.interfaceBuilder(value.type.kotlinApi)
        .setDeclaration(value)
        .addSuperinterface(Symbols.Brevity.Resource)
        .apply {
          for (function in value.functions) {
            if (!function.isSupported) continue
            // Don't override close(), it's inherited from the 'Resource' supertype.
            if (function.functionName is FunctionName.ResourceDrop) continue
            addFunction(ApiFunctionFactory(function).api())
          }
        }
        .build(),
    )
  }

  context(collector: QualifiedSpecCollector)
  private fun generateVariant(value: IrVariant) {
    collector.addType(
      value.type.kotlinApi,
      TypeSpec.interfaceBuilder(value.type.kotlinApi)
        .addModifiers(KModifier.SEALED)
        .setDeclaration(value)
        .apply {
          for (case in value.cases) {
            val type = case.type
            if (type != null) {
              addType(
                TypeSpec.classBuilder(case.kotlinName)
                  .addModifiers(KModifier.DATA)
                  .addSuperinterface(value.type.kotlinApi)
                  .primaryConstructor(
                    FunSpec.constructorBuilder()
                      .addParameter("value", type.kotlinApi)
                      .build(),
                  )
                  .addProperty(
                    PropertySpec.builder("value", type.kotlinApi)
                      .initializer("%N", "value")
                      .build(),
                  )
                  .setDeclaration(case)
                  .build(),
              )
            } else {
              addType(
                TypeSpec.objectBuilder(case.kotlinName)
                  .addModifiers(KModifier.DATA)
                  .addSuperinterface(value.type.kotlinApi)
                  .setDeclaration(case)
                  .build(),
              )
            }
          }
        }
        .build(),
    )
  }

  context(collector: QualifiedSpecCollector)
  private fun generateEnum(value: IrEnum) {
    collector.addType(
      className = value.type.kotlinApi,
      type = TypeSpec.enumBuilder(value.type.kotlinApi)
        .setDeclaration(value)
        .apply {
          for (case in value.cases) {
            addEnumConstant(
              case.kotlinName,
              TypeSpec.anonymousClassBuilder()
                .setDeclaration(case)
                .build(),
            )
          }
        }
        .build(),
    )
  }

  context(collector: QualifiedSpecCollector)
  private fun generateTypeAlias(value: IrTypeAlias) {
    collector.addType(
      className = value.type.kotlinApi,
      type = TypeSpec.classBuilder(value.type.kotlinApi)
        .addModifiers(KModifier.VALUE)
        .addAnnotation(JvmInline::class)
        .setDeclaration(value)
        .apply {
          val parameter = ParameterSpec.builder("value", value.target.kotlinApi)
            .build()

          primaryConstructor(
            FunSpec.constructorBuilder()
              .addParameter(parameter)
              .build(),
          )

          addProperty(
            PropertySpec.builder("value", value.target.kotlinApi)
              .initializer("%N", parameter)
              .build(),
          )
        }
        .build(),
    )
  }

  context(collector: QualifiedSpecCollector)
  private fun generateFlags(value: IrFlags) {
    collector.addType(
      className = value.type.kotlinApi,
      type = TypeSpec.classBuilder(value.type.kotlinApi)
        .addModifiers(KModifier.DATA)
        .setDeclaration(value)
        .apply {
          val constructorBuilder = FunSpec.constructorBuilder()

          for (field in value.flags) {
            val parameter = ParameterSpec.builder(field.kotlinName, BOOLEAN)
              .build()
            constructorBuilder.addParameter(parameter)
            addProperty(
              PropertySpec.builder(field.kotlinName, BOOLEAN)
                .initializer("%N", parameter)
                .setDeclaration(field)
                .build(),
            )
          }

          primaryConstructor(constructorBuilder.build())
        }
        .build(),
    )
  }

  context(collector: QualifiedSpecCollector)
  private fun generateApi(value: IrWitPackage.Service) {
    if (!value.hasInstanceMembers && value.types.isEmpty()) return

    val typeName = value.serviceName.kotlinApi

    val builder = when {
      !value.hasInstanceMembers || value is IrWorld -> TypeSpec.objectBuilder(typeName)
      else -> TypeSpec.interfaceBuilder(typeName)
    }

    value.documentation?.let {
      builder.addKdoc(it.content.trimIndent())
    }

    for (type in value.types) {
      when (type) {
        is IrEnum -> generateEnum(type)
        is IrFlags -> generateFlags(type)
        is IrRecord -> generateRecord(type)
        is IrResource -> generateResource(type)
        is IrTypeAlias -> generateTypeAlias(type)
        is IrVariant -> generateVariant(type)
      }
    }

    when (value) {
      is IrInterface -> {
        for (function in value.functions) {
          builder.addFunction(ApiFunctionFactory(function).api())
        }
      }

      is IrWorld -> {
        val guestApis = value.guestApis
        if (guestApis != null) {
          generateExternalApis(guestApis)
        }
        val hostApis = value.hostApis
        if (hostApis != null) {
          generateExternalApis(hostApis)
        }
      }
    }

    collector.addType(typeName, builder.build())
  }

  context(collector: QualifiedSpecCollector)
  private fun generateExternalApis(value: ExternalApis) {
    collector.addType(
      className = value.type,
      type = TypeSpec.interfaceBuilder(value.type)
        .apply {
          for (item in value.items) {
            when (item) {
              is IrExternalApi -> addProperty(item.instanceName, item.serviceName.kotlinApi)
              is IrFunction -> addFunction(ApiFunctionFactory(item).api())
            }
          }
        }
        .build(),
    )
  }
}
